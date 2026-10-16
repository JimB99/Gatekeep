package com.gatekeep.app.enforcement

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.gatekeep.app.MainActivity
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gatekeep.app.R
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.BlockMessageResolver
import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.app.util.formatDurationMs
import com.gatekeep.app.util.withAppLocale
import com.gatekeep.domain.FrictionChallenge
import com.gatekeep.domain.model.BlockPresentationReason
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.MathChallenge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: dagger.Lazy<EnforcementCoordinator>,
    private val screenStateMonitor: ScreenStateMonitor,
    private val enforcementLog: EnforcementLog,
) {
    private val localizedContext = context.withAppLocale()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private val attachedOverlayViews = ArrayList<View>(2)
    private var waitRunnable: Runnable? = null
    private var breakRunnable: Runnable? = null
    private var waitStopwatch: PausedStopwatch? = null
    @Volatile private var waitActive = false
    private var frictionInProgress = false
    private var currentChallenge: MathChallenge? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentRequest: BlockOverlayRequest? = null
    @Volatile
    private var overlayVisible = false
    private var overlayEpoch = 0L

    fun isVisible(): Boolean = overlayVisible

    fun hasActiveWait(): Boolean = waitActive

    fun blockedPackage(): String? = if (overlayVisible) currentRequest?.packageName else null

    fun isFrictionInProgress(): Boolean = frictionInProgress

    fun clearFrictionState() {
        frictionInProgress = false
        currentChallenge = null
    }

    @SuppressLint("InflateParams")
    fun show(request: BlockOverlayRequest) {
        val epoch = overlayEpoch
        runOnMain {
            try {
                if (epoch != overlayEpoch) {
                    coordinator.get().onOverlayShowAborted()
                    return@runOnMain
                }
                if (!coordinator.get().shouldPresentBlockOverlay(request.packageName)) {
                    coordinator.get().onOverlayShowAborted()
                    return@runOnMain
                }
                currentRequest = request
                if (overlayView != null && frictionInProgress) return@runOnMain
                if (overlayView != null) {
                    overlayView?.let { restoreInteractive(it) }
                    overlayVisible = true
                    updateBlock(request.message, request.reason, request.breakUntilMs)
                    startBreakTicker(request.breakUntilMs)
                    return@runOnMain
                }
                createBlockOverlay(request)
            } catch (e: Exception) {
                coordinator.get().onOverlayShowAborted()
                enforcementLog.logError("Block overlay show failed", e)
            }
        }
    }

    fun showDelay(delaySeconds: Int, message: String, onComplete: () -> Unit) {
        val epoch = overlayEpoch
        runOnMain {
            if (!OverlayPresentPolicy.shouldRunPostedShow(epoch, overlayEpoch)) return@runOnMain
            try {
                clearOverlayState()
                removeOverlayOnly()
                if (!OverlayPresentPolicy.shouldRunPostedShow(epoch, overlayEpoch)) return@runOnMain
                val view = LayoutInflater.from(localizedContext).inflate(R.layout.overlay_block, null)
                applyChrome(view, BlockPresentationReason.openGate)
                view.findViewById<TextView>(R.id.block_message).text = message
                view.findViewById<LinearLayout>(R.id.friction_container).visibility = View.VISIBLE
                view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE
                view.findViewById<Button>(R.id.block_back_btn).visibility = View.GONE
                view.findViewById<LinearLayout>(R.id.extension_buttons).visibility = View.GONE
                val countdown = view.findViewById<TextView>(R.id.wait_countdown)
                countdown.visibility = View.VISIBLE
                var remaining = delaySeconds
                countdown.text = localizedContext.getString(R.string.countdown_seconds_format, remaining)
                val stopwatch = screenStateMonitor.createStopwatch()
                addOverlay(view, focusable = false)
                waitActive = true
                waitRunnable = object : Runnable {
                    override fun run() {
                        if (!waitActive) return
                        if (!OverlayPresentPolicy.shouldRunPostedShow(epoch, overlayEpoch)) {
                            abortStaleWaitTicker(this)
                            return
                        }
                        if (screenStateMonitor.isWaitClockFrozen()) {
                            if (waitActive) mainHandler.postDelayed(this, 500)
                            return
                        }
                        val elapsedSec = (stopwatch.elapsedMs() / 1000).toInt()
                        remaining = (delaySeconds - elapsedSec).coerceAtLeast(0)
                        if (remaining <= 0) {
                            waitActive = false
                            removeOverlayOnly()
                            if (OverlayPresentPolicy.shouldRunPostedShow(epoch, overlayEpoch)) {
                                onComplete()
                            }
                        } else {
                            countdown.text = localizedContext.getString(R.string.countdown_seconds_format, remaining)
                            if (waitActive && OverlayPresentPolicy.shouldRunPostedShow(epoch, overlayEpoch)) {
                                mainHandler.postDelayed(this, 1000)
                            }
                        }
                    }
                }
                mainHandler.postDelayed(waitRunnable!!, 1000)
            } catch (e: Exception) {
                enforcementLog.logError("Block overlay delay show failed", e)
            }
        }
    }

    fun hideTemporarily() {
        overlayEpoch++
        runOnMainSync {
            clearOverlayTimers()
            removeOverlayOnly()
            // RES-02: frictionInProgress guards active math/wait UI, not an unresolved limit decision.
            frictionInProgress = LimitOverlayHidePolicy.frictionInProgressAfterTemporaryHide()
        }
    }

    fun reshowFromStoredRequest() {
        val epoch = ++overlayEpoch
        mainHandler.post {
            if (epoch != overlayEpoch) return@post
            val request = currentRequest
            if (request == null) {
                coordinator.get().onOverlayShowAborted()
                return@post
            }
            if (!coordinator.get().shouldPresentBlockOverlay(request.packageName)) {
                coordinator.get().onOverlayShowAborted()
                return@post
            }
            if (overlayVisible || overlayView != null) return@post
            createBlockOverlay(request)
        }
    }

    fun dismissByUser() {
        overlayEpoch++
        mainHandler.post {
            clearOverlayState()
            removeOverlayOnly()
            coordinator.get().onBlockDismissed()
        }
    }

    fun removeAfterResolution() {
        overlayEpoch++
        val work = {
            clearOverlayState()
            removeOverlayOnly()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mainHandler.post(work)
        } else {
            runOnMainSync(block = work)
        }
    }

    fun removeOverlaySynchronously(timeoutMs: Long = 3_000L) {
        overlayEpoch++
        runOnMainSync(timeoutMs) {
            clearOverlayState()
            removeOverlayOnly()
            if (overlayView?.isAttachedToWindow == true) {
                removeOverlayOnly()
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun runOnMainSync(timeoutMs: Long = 3_000L, block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        val error = arrayOfNulls<Throwable>(1)
        mainHandler.post {
            try {
                block()
            } catch (t: Throwable) {
                error[0] = t
            } finally {
                latch.countDown()
            }
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        error[0]?.let { throw it }
    }

    private fun clearOverlayTimers() {
        waitRunnable?.let { mainHandler.removeCallbacks(it) }
        waitRunnable = null
        breakRunnable?.let { mainHandler.removeCallbacks(it) }
        breakRunnable = null
        waitStopwatch = null
        waitActive = false
    }

    private fun clearOverlayState() {
        clearOverlayTimers()
        frictionInProgress = false
        currentChallenge = null
        currentRequest = null
    }

    private fun abortStaleWaitTicker(ticker: Runnable) {
        waitActive = false
        if (OverlayPresentPolicy.shouldRemoveOnStaleWaitTick(waitRunnable === ticker)) {
            removeOverlayOnly()
        }
    }

    private fun removeOverlayOnly() {
        val views = (attachedOverlayViews + listOfNotNull(overlayView)).distinct()
        if (views.isEmpty()) {
            overlayView = null
            overlayParams = null
            overlayVisible = LimitOverlayHidePolicy.overlayVisibleAfterHideRequested(
                removeCompleted = true,
            )
            return
        }
        views.forEach { view ->
            view.clearFocus()
            neutralizeForRemove(view)
            repeat(3) {
                if (!isInWindowManager(view)) return@repeat
                runCatching { windowManager.removeViewImmediate(view) }
                    .recoverCatching { windowManager.removeView(view) }
                    .onFailure { e ->
                        enforcementLog.logError(
                            "Block overlay remove failed attached=${view.isAttachedToWindow}",
                            e,
                        )
                    }
            }
        }
        val stillInWm = views.filter { isInWindowManager(it) }
        attachedOverlayViews.clear()
        if (stillInWm.isNotEmpty()) {
            attachedOverlayViews.addAll(stillInWm)
            overlayView = stillInWm.last()
            overlayVisible = LimitOverlayHidePolicy.overlayVisibleAfterHideRequested(
                removeCompleted = false,
            )
            return
        }
        overlayView = null
        overlayParams = null
        overlayVisible = LimitOverlayHidePolicy.overlayVisibleAfterHideRequested(
            removeCompleted = true,
        )
    }

    private fun neutralizeForRemove(view: View) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.alpha = 0f
        view.visibility = View.GONE
        val params = overlayParams ?: (view.layoutParams as? WindowManager.LayoutParams) ?: return
        if (!LimitOverlayHidePolicy.consumeTouches(hideRequested = true)) {
            params.width = 1
            params.height = 1
            params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        overlayParams = params
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun restoreInteractive(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        overlayParams = params
        if (isInWindowManager(view)) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun isInWindowManager(view: View): Boolean {
        if (view.isAttachedToWindow) return true
        val params = overlayParams ?: (view.layoutParams as? WindowManager.LayoutParams)
            ?: return false
        return try {
            windowManager.updateViewLayout(view, params)
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("InflateParams")
    private fun createBlockOverlay(request: BlockOverlayRequest) {
        val view = LayoutInflater.from(localizedContext).inflate(R.layout.overlay_block, null)
        updateBlockView(view, request.message, request.reason, request.breakUntilMs)

        view.findViewById<TextView>(R.id.overlay_title)?.apply {
            isClickable = true
            setOnClickListener {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                coordinator.get().onOpenGatekeepFromOverlay()
            }
        }

        val challenge = if (request.frictionMethod == FrictionMethod.math) {
            FrictionChallenge.generate(request.difficulty).also { currentChallenge = it }
        } else null

        view.findViewById<Button>(R.id.block_back_btn).apply {
            styleTextButton(this)
            setOnClickListener {
                ForegroundMonitorAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME,
                )
                dismissByUser()
            }
        }

        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        // Not focusable: FLAG_ALT_FOCUSABLE_IM + focusable overlay stalls the main thread (IME churn / ANR).
        addOverlay(view, focusable = false)
        startBreakTicker(request.breakUntilMs)

        val continueBtn = view.findViewById<Button>(R.id.block_continue_btn)
        val extensionContainer = view.findViewById<LinearLayout>(R.id.extension_buttons)
        val timeButtons = view.findViewById<LinearLayout>(R.id.extension_time_buttons)
        val noLimitContainer = view.findViewById<LinearLayout>(R.id.extension_no_limit_container)

        val extensionLayout = OverlayExtensionPanelPolicy.layout(request)
        if (!request.bypassAllowed) {
            continueBtn.visibility = View.GONE
            extensionContainer.visibility = View.GONE
        } else if (extensionLayout.showPanel) {
            continueBtn.visibility = View.GONE
            extensionContainer.visibility = View.VISIBLE
            bindExtensionQuota(view, request)
            timeButtons.removeAllViews()
            noLimitContainer.removeAllViews()
            val density = context.resources.displayMetrics.density
            val hMargin = (4 * density).toInt()
            val vPadding = (8 * density).toInt()
            extensionLayout.minuteOptions.forEach { minutes ->
                val btn = Button(localizedContext).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    text = localizedContext.getString(R.string.extension_minutes_format, minutes)
                    styleFilledButton(
                        this,
                        R.drawable.bg_overlay_button_filled,
                        localizedContext.getColor(R.color.gatekeep_on_primary),
                    )
                    setPadding((12 * density).toInt(), vPadding, (12 * density).toInt(), vPadding)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = hMargin
                        marginEnd = hMargin
                        topMargin = vPadding / 2
                        bottomMargin = vPadding / 2
                    }
                    setOnClickListener {
                        coordinator.get().grantExtensionMinutes(
                            request.packageName,
                            minutes,
                            OverlayFrictionGrantPolicy.blockReason(request.reason),
                        )
                    }
                }
                timeButtons.addView(btn)
            }
            if (extensionLayout.showNoLimitToday) {
                val noLimitBtn = Button(localizedContext).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    text = localizedContext.getString(R.string.overlay_no_limit_today)
                    styleFilledButton(
                        this,
                        R.drawable.bg_overlay_button_tertiary,
                        localizedContext.getColor(R.color.gatekeep_on_tertiary),
                    )
                    setPadding((16 * density).toInt(), vPadding, (16 * density).toInt(), vPadding)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                    setOnClickListener {
                        coordinator.get().grantNoLimitToday(request.packageName)
                    }
                }
                noLimitContainer.addView(noLimitBtn)
            }
        } else if (request.isOpenGate) {
            extensionContainer.visibility = View.GONE
            continueBtn.visibility = View.GONE
            frictionInProgress = true
            showFriction(view, request.frictionMethod, challenge, request)
        } else {
            extensionContainer.visibility = View.GONE
            if (request.frictionMethod == FrictionMethod.none) {
                continueBtn.visibility = View.GONE
            } else if (request.frictionMethod == FrictionMethod.waitOneMin) {
                continueBtn.visibility = View.GONE
                frictionInProgress = true
                showFriction(view, request.frictionMethod, challenge, request)
            } else {
                continueBtn.visibility = View.VISIBLE
                continueBtn.setOnClickListener {
                    showFriction(
                        view,
                        request.frictionMethod,
                        challenge,
                        request,
                    )
                }
            }
        }

    }

    private fun showFriction(
        view: View,
        method: FrictionMethod,
        challenge: MathChallenge?,
        request: BlockOverlayRequest,
    ) {
        frictionInProgress = true

        val container = view.findViewById<LinearLayout>(R.id.friction_container)
        container.visibility = View.VISIBLE
        view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE

        when (method) {
            FrictionMethod.none -> {
                container.visibility = View.GONE
                frictionInProgress = false
            }
            FrictionMethod.math -> {
                makeOverlayFocusableForInput()
                val mathChallenge = challenge ?: FrictionChallenge.generate(request.difficulty)
                    .also { currentChallenge = it }
                showMathFriction(view, mathChallenge, request)
            }
            FrictionMethod.password -> {
                makeOverlayFocusableForInput()
                if (!request.profilePasswordHash.isNullOrBlank()) {
                    showPasswordFriction(view, request)
                } else {
                    val mathChallenge = FrictionChallenge.generate(request.difficulty)
                        .also { currentChallenge = it }
                    showMathFriction(view, mathChallenge, request)
                }
            }
            FrictionMethod.waitOneMin -> {
                view.findViewById<TextView>(R.id.friction_prompt).text =
                    localizedContext.getString(R.string.overlay_wait_to_continue)
                val countdown = view.findViewById<TextView>(R.id.wait_countdown)
                countdown.visibility = View.VISIBLE
                val totalMs = request.waitDurationSeconds * 1000L
                if (totalMs <= 0L) {
                    coordinator.get().onWaitCompleted(request.packageName)
                    onFrictionSuccess(request)
                    return
                }
                coordinator.get().onWaitStarted(
                    request.packageName,
                    System.currentTimeMillis() + totalMs,
                )
                val stopwatch = screenStateMonitor.createStopwatch().also { waitStopwatch = it }
                waitActive = true
                val waitEpoch = overlayEpoch
                waitRunnable = object : Runnable {
                    override fun run() {
                        if (!waitActive) return
                        if (!OverlayPresentPolicy.shouldRunPostedShow(waitEpoch, overlayEpoch)) {
                            abortStaleWaitTicker(this)
                            return
                        }
                        if (screenStateMonitor.isWaitClockFrozen()) {
                            if (waitActive) mainHandler.postDelayed(this, 500)
                            return
                        }
                        val remainingMs = stopwatch.remainingMs(totalMs)
                        val remainingSec = ((remainingMs + 999) / 1000).toInt()
                        if (remainingSec <= 0) {
                            frictionInProgress = false
                            waitActive = false
                            waitStopwatch = null
                            coordinator.get().onWaitCompleted(request.packageName)
                            onFrictionSuccess(request)
                        } else {
                            countdown.text = localizedContext.getString(R.string.countdown_seconds_format, remainingSec)
                            if (waitActive &&
                                OverlayPresentPolicy.shouldRunPostedShow(waitEpoch, overlayEpoch)
                            ) {
                                mainHandler.postDelayed(this, 1000)
                            }
                        }
                    }
                }
                countdown.text = localizedContext.getString(
                    R.string.countdown_seconds_format,
                    request.waitDurationSeconds,
                )
                mainHandler.postDelayed(waitRunnable!!, 1000)
            }
            else -> {
                view.findViewById<TextView>(R.id.friction_prompt).text =
                    localizedContext.getString(R.string.overlay_use_math_or_wait)
            }
        }
    }

    private fun onFrictionSuccess(request: BlockOverlayRequest) {
        when (OverlayFrictionGrantPolicy.grant(request)) {
            OverlayFrictionGrantPolicy.Grant.OpenGate -> {
                coordinator.get().onFrictionCompleted(request.packageName)
                coordinator.get().onOpenGatePassed(request.packageName)
            }
            OverlayFrictionGrantPolicy.Grant.SessionRefill ->
                coordinator.get().grantSessionRefill(request.packageName)
            OverlayFrictionGrantPolicy.Grant.PeriodContinue ->
                coordinator.get().grantPeriodContinue(request.packageName)
            OverlayFrictionGrantPolicy.Grant.None -> {
                coordinator.get().onFrictionCompleted(request.packageName)
                dismissByUser()
                coordinator.get().refresh()
            }
        }
    }

    private fun showPasswordFriction(view: View, request: BlockOverlayRequest) {
        makeOverlayFocusableForInput()
        val container = view.findViewById<LinearLayout>(R.id.friction_container)
        container.visibility = View.VISIBLE
        view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE
        view.findViewById<TextView>(R.id.friction_prompt).text =
            localizedContext.getString(R.string.overlay_enter_profile_pin)
        val input = view.findViewById<EditText>(R.id.friction_input)
        input.visibility = View.VISIBLE
        prepareInputField(input)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val submit = view.findViewById<Button>(R.id.friction_submit)
        submit.visibility = View.VISIBLE
        submit.text = localizedContext.getString(R.string.unlock)
        submit.setOnClickListener {
            val pin = input.text.toString()
            if (!request.profilePasswordHash.isNullOrBlank() &&
                PasswordHasher.verify(pin, request.profilePasswordHash)
            ) {
                frictionInProgress = false
                if (request.isOpenGate) {
                    coordinator.get().onOpenGatePassed(request.packageName)
                } else if (request.useExtensionButtons) {
                    dismissByUser()
                    coordinator.get().refresh()
                } else {
                    dismissByUser()
                    coordinator.get().refresh()
                }
            }
        }
        showKeyboard(input)
    }

    private fun showMathFriction(view: View, challenge: MathChallenge, request: BlockOverlayRequest) {
        view.findViewById<TextView>(R.id.friction_prompt).text = challenge.question
        val input = view.findViewById<EditText>(R.id.friction_input)
        input.visibility = View.VISIBLE
        prepareInputField(input)
        val submit = view.findViewById<Button>(R.id.friction_submit)
        submit.visibility = View.VISIBLE
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.performClick()
                true
            } else false
        }
        submit.setOnClickListener {
            val answer = input.text.toString().toIntOrNull() ?: -1
            if (FrictionChallenge.verify(challenge, answer)) {
                frictionInProgress = false
                onFrictionSuccess(request)
            }
        }
        showKeyboard(input)
    }

    private fun scrollInputIntoView(input: EditText) {
        var parent = input.parent
        while (parent != null && parent !is ScrollView) {
            parent = parent.parent
        }
        (parent as? ScrollView)?.post {
            val scrollY = input.bottom - (parent.height * 0.6f).toInt()
            parent.smoothScrollTo(0, scrollY.coerceAtLeast(0))
        }
    }

    private fun prepareInputField(input: EditText) {
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollInputIntoView(input)
        }
    }

    private fun makeOverlayFocusableForInput() {
        overlayParams?.let { params ->
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            overlayView?.let { windowManager.updateViewLayout(it, params) }
        }
        overlayView?.requestFocus()
    }

    private fun showKeyboard(input: EditText) {
        input.requestFocus()
        mainHandler.postDelayed({
            val shown = inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_FORCED)
            if (!shown) {
                mainHandler.postDelayed({
                    inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_FORCED)
                }, 200)
            }
        }, 200)
    }

    private fun updateBlock(message: String, reason: BlockPresentationReason, breakUntilMs: Long?) {
        if (frictionInProgress) return
        overlayView?.let { updateBlockView(it, message, reason, breakUntilMs) }
        startBreakTicker(breakUntilMs)
    }

    private fun startBreakTicker(breakUntilMs: Long?) {
        breakRunnable?.let { mainHandler.removeCallbacks(it) }
        breakRunnable = null
        if (breakUntilMs == null) return
        val now = System.currentTimeMillis()
        if (breakUntilMs <= now) {
            currentRequest?.packageName?.let { coordinator.get().onBreakExpired(it) }
            return
        }
        val breakText = overlayView?.findViewById<TextView>(R.id.block_break_text) ?: return
        breakText.visibility = View.VISIBLE
        var breakExpiredHandled = false
        breakRunnable = object : Runnable {
            override fun run() {
                val remaining = breakUntilMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    breakText.text = localizedContext.getString(R.string.overlay_break_ended)
                    if (!breakExpiredHandled) {
                        breakExpiredHandled = true
                        currentRequest?.packageName?.let { coordinator.get().onBreakExpired(it) }
                    }
                } else {
                    breakText.text = localizedContext.getString(
                        R.string.overlay_break_ends_in,
                        formatDurationMs(context, remaining),
                    )
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
        mainHandler.post(breakRunnable!!)
    }

    private fun bindExtensionQuota(view: View, request: BlockOverlayRequest) {
        val headingView = view.findViewById<TextView>(R.id.extension_quota_heading)
        val quotaView = view.findViewById<TextView>(R.id.extension_quota_text)
        headingView.text = localizedContext.getString(R.string.extension_quota_heading)
        headingView.visibility = View.VISIBLE
        val todayText = request.maxExtensionsPerDay?.let { max ->
            localizedContext.getString(R.string.extension_quota_today, request.extensionsUsedToday, max)
        } ?: localizedContext.getString(
            R.string.extension_quota_today_unlimited,
            request.extensionsUsedToday,
        )
        val consecutiveText = request.maxConsecutiveExtensions?.let { max ->
            localizedContext.getString(
                R.string.extension_quota_consecutive,
                request.consecutiveExtensionsUsed,
                max,
            )
        } ?: localizedContext.getString(
            R.string.extension_quota_consecutive_unlimited,
            request.consecutiveExtensionsUsed,
        )
        quotaView.text = "$todayText · $consecutiveText"
        quotaView.visibility = View.VISIBLE
    }

    private fun updateBlockView(view: View, message: String, reason: BlockPresentationReason, breakUntilMs: Long?) {
        applyChrome(view, reason)
        view.findViewById<TextView>(R.id.block_message).text = message
        view.findViewById<TextView>(R.id.block_reason).visibility = View.GONE
        val breakText = view.findViewById<TextView>(R.id.block_break_text)
        if (breakUntilMs != null) {
            breakText.visibility = View.VISIBLE
            breakText.text = localizedContext.getString(
                R.string.overlay_break_ends_in,
                formatDurationMs(context, breakUntilMs - System.currentTimeMillis()),
            )
        } else {
            breakText.visibility = View.GONE
        }
    }

    private fun applyChrome(view: View, reason: BlockPresentationReason) {
        val chrome = OverlayChromePolicy.chrome(reason)
        val accentColor = localizedContext.getColor(accentColorRes(chrome.accent))
        view.findViewById<View>(R.id.overlay_accent).setBackgroundColor(accentColor)
        val titleView = view.findViewById<TextView>(R.id.overlay_title)
        val title = BlockMessageResolver.chromeTitle(localizedContext, reason)
        titleView.text = title
        titleView.setTextColor(
            if (chrome.accent == OverlayAccentFamily.Default) {
                localizedContext.getColor(R.color.gatekeep_on_surface)
            } else {
                accentColor
            },
        )
    }

    private fun accentColorRes(family: OverlayAccentFamily): Int = when (family) {
        OverlayAccentFamily.OnOpen -> R.color.gatekeep_primary
        OverlayAccentFamily.Session -> R.color.gatekeep_tertiary
        OverlayAccentFamily.Period -> R.color.gatekeep_secondary
        OverlayAccentFamily.Default -> R.color.gatekeep_outline
    }

    private fun styleFilledButton(button: Button, backgroundRes: Int, textColor: Int) {
        button.setBackgroundResource(backgroundRes)
        button.backgroundTintList = null
        button.setTextColor(textColor)
        button.isAllCaps = false
        button.minHeight = localizedContext.resources.getDimensionPixelSize(R.dimen.overlay_button_min_height)
        button.elevation = 0f
        button.stateListAnimator = null
    }

    private fun styleTextButton(button: Button) {
        button.setBackgroundResource(R.drawable.bg_overlay_button_text)
        button.setTextColor(localizedContext.getColor(R.color.gatekeep_on_surface_variant))
        button.isAllCaps = false
        button.minHeight = localizedContext.resources.getDimensionPixelSize(R.dimen.overlay_button_min_height)
        button.elevation = 0f
        button.stateListAnimator = null
    }

    private fun addOverlay(view: View, focusable: Boolean) {
        clearOverlayTimers()
        removeOverlayOnly()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (focusable) {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            } else {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            },
            PixelFormat.TRANSLUCENT,
        )
        try {
            windowManager.addView(view, params)
            overlayView = view
            overlayParams = params
            attachedOverlayViews.add(view)
            overlayVisible = true
        } catch (e: Exception) {
            overlayView = null
            overlayParams = null
            overlayVisible = false
            enforcementLog.logError("Block overlay add failed", e)
            throw e
        }
    }
}
