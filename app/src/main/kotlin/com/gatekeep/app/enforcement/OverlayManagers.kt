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
import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.app.util.formatDurationMs
import com.gatekeep.app.util.withAppLocale
import com.gatekeep.domain.FrictionChallenge
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.MathChallenge
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private var waitRunnable: Runnable? = null
    private var breakRunnable: Runnable? = null
    private var waitStopwatch: ScreenStateMonitor.PausedStopwatch? = null
    private var frictionInProgress = false
    private var currentChallenge: MathChallenge? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentRequest: BlockOverlayRequest? = null
    @Volatile
    private var overlayVisible = false

    fun isVisible(): Boolean = overlayVisible

    fun blockedPackage(): String? = if (overlayVisible) currentRequest?.packageName else null

    fun isFrictionInProgress(): Boolean = frictionInProgress

    fun clearFrictionState() {
        frictionInProgress = false
        currentChallenge = null
    }

    @SuppressLint("InflateParams")
    fun show(request: BlockOverlayRequest) {
        mainHandler.post {
            try {
                currentRequest = request
                if (overlayView != null && frictionInProgress) return@post
                if (overlayView != null) {
                    updateBlock(request.message, request.reason, request.breakUntilMs)
                    startBreakTicker(request.breakUntilMs)
                    return@post
                }
                createBlockOverlay(request)
            } catch (e: Exception) {
                enforcementLog.logError("Block overlay show failed", e)
            }
        }
    }

    fun showDelay(delaySeconds: Int, message: String, onComplete: () -> Unit) {
        mainHandler.post {
            try {
                clearOverlayState()
                removeOverlayOnly()
                val view = LayoutInflater.from(localizedContext).inflate(R.layout.overlay_block, null)
                view.findViewById<TextView>(R.id.block_message).text = message
                view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE
                view.findViewById<Button>(R.id.block_back_btn).visibility = View.GONE
                view.findViewById<LinearLayout>(R.id.extension_buttons).visibility = View.GONE
                val countdown = view.findViewById<TextView>(R.id.wait_countdown)
                countdown.visibility = View.VISIBLE
                var remaining = delaySeconds
                countdown.text = "${remaining}s"
                val stopwatch = screenStateMonitor.createStopwatch()
                addOverlay(view, focusable = false)
                waitRunnable = object : Runnable {
                    override fun run() {
                        val elapsedSec = (stopwatch.elapsedMs() / 1000).toInt()
                        remaining = (delaySeconds - elapsedSec).coerceAtLeast(0)
                        if (remaining <= 0) {
                            removeOverlayOnly()
                            onComplete()
                        } else {
                            countdown.text = "${remaining}s"
                            mainHandler.postDelayed(this, 1000)
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
        mainHandler.post {
            clearOverlayTimers()
            removeOverlayOnly()
        }
    }

    fun dismissByUser() {
        mainHandler.post {
            clearOverlayState()
            removeOverlayOnly()
            coordinator.get().onBlockDismissed()
        }
    }

    fun removeAfterResolution() {
        mainHandler.post {
            clearOverlayState()
            removeOverlayOnly()
        }
    }

    private fun clearOverlayTimers() {
        waitRunnable?.let { mainHandler.removeCallbacks(it) }
        waitRunnable = null
        breakRunnable?.let { mainHandler.removeCallbacks(it) }
        breakRunnable = null
        waitStopwatch = null
    }

    private fun clearOverlayState() {
        clearOverlayTimers()
        frictionInProgress = false
        currentChallenge = null
        currentRequest = null
    }

    private fun removeOverlayOnly() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
                .onFailure { e -> enforcementLog.logError("Block overlay remove failed", e) }
            overlayView = null
            overlayParams = null
        }
        overlayVisible = false
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
            setBackgroundColor(0xFF424242.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                ForegroundMonitorAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME,
                )
                dismissByUser()
            }
        }

        addOverlay(view, focusable = false)
        startBreakTicker(request.breakUntilMs)

        val continueBtn = view.findViewById<Button>(R.id.block_continue_btn)
        val extensionContainer = view.findViewById<LinearLayout>(R.id.extension_buttons)
        val timeButtons = view.findViewById<LinearLayout>(R.id.extension_time_buttons)
        val noLimitContainer = view.findViewById<LinearLayout>(R.id.extension_no_limit_container)

        if (!request.bypassAllowed) {
            continueBtn.visibility = View.GONE
            extensionContainer.visibility = View.GONE
        } else if (request.useExtensionButtons && request.extensionOptionMinutes.isNotEmpty()) {
            continueBtn.visibility = View.GONE
            extensionContainer.visibility = View.VISIBLE
            bindExtensionQuota(view, request)
            timeButtons.removeAllViews()
            noLimitContainer.removeAllViews()
            val density = context.resources.displayMetrics.density
            val hMargin = (4 * density).toInt()
            val vPadding = (8 * density).toInt()
            request.extensionOptionMinutes.forEach { minutes ->
                val btn = Button(context).apply {
                    text = localizedContext.getString(R.string.extension_minutes_format, minutes)
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF1976D2.toInt())
                    setPadding((12 * density).toInt(), vPadding, (12 * density).toInt(), vPadding)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = hMargin
                        marginEnd = hMargin
                    }
                    setOnClickListener {
                        coordinator.get().grantExtensionMinutes(request.packageName, minutes)
                    }
                }
                timeButtons.addView(btn)
            }
            if (request.showNoLimitToday) {
                val noLimitBtn = Button(context).apply {
                    text = localizedContext.getString(R.string.overlay_no_limit_today)
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFFEF6C00.toInt())
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

        val hasProfilePin = !request.profilePasswordHash.isNullOrBlank()
        if (request.reason == "profilePin" && hasProfilePin) {
            frictionInProgress = true
            continueBtn.visibility = View.GONE
            showPasswordFriction(view, request)
        }
    }

    private fun showFriction(
        view: View,
        method: FrictionMethod,
        challenge: MathChallenge?,
        request: BlockOverlayRequest,
    ) {
        frictionInProgress = true
        makeOverlayFocusableForInput()

        val container = view.findViewById<LinearLayout>(R.id.friction_container)
        container.visibility = View.VISIBLE
        view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE

        when (method) {
            FrictionMethod.math -> {
                val mathChallenge = challenge ?: FrictionChallenge.generate(request.difficulty)
                    .also { currentChallenge = it }
                showMathFriction(view, mathChallenge, request)
            }
            FrictionMethod.password -> {
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
                val wallClockDeadlineMs = if (request.waitWallClock) {
                    System.currentTimeMillis() + totalMs
                } else {
                    null
                }
                if (wallClockDeadlineMs != null) {
                    coordinator.get().onWaitStarted(request.packageName, wallClockDeadlineMs)
                }
                val stopwatch = if (wallClockDeadlineMs == null) {
                    screenStateMonitor.createStopwatch().also { waitStopwatch = it }
                } else {
                    null
                }
                waitRunnable = object : Runnable {
                    override fun run() {
                        val remainingMs = if (wallClockDeadlineMs != null) {
                            wallClockDeadlineMs - System.currentTimeMillis()
                        } else {
                            stopwatch!!.remainingMs(totalMs)
                        }
                        val remainingSec = ((remainingMs + 999) / 1000).toInt()
                        if (remainingSec <= 0) {
                            frictionInProgress = false
                            waitStopwatch = null
                            if (wallClockDeadlineMs != null) {
                                coordinator.get().onWaitCompleted(request.packageName)
                            }
                            onFrictionSuccess(request)
                        } else {
                            countdown.text = "${remainingSec}s"
                            mainHandler.postDelayed(this, 1000)
                        }
                    }
                }
                countdown.text = "${request.waitDurationSeconds}s"
                mainHandler.postDelayed(waitRunnable!!, 1000)
            }
            else -> {
                view.findViewById<TextView>(R.id.friction_prompt).text =
                    localizedContext.getString(R.string.overlay_use_math_or_wait)
            }
        }
    }

    private fun onFrictionSuccess(request: BlockOverlayRequest) {
        coordinator.get().onFrictionCompleted(request.packageName)
        if (request.isOpenGate) {
            coordinator.get().onOpenGatePassed(request.packageName)
        } else {
            coordinator.get().grantExtensionMinutes(
                request.packageName,
                request.extensionOptionMinutes.firstOrNull() ?: 5,
            )
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
                request.onProfileUnlocked?.invoke()
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

    private fun updateBlock(message: String, reason: String, breakUntilMs: Long?) {
        if (frictionInProgress) return
        overlayView?.let { updateBlockView(it, message, reason, breakUntilMs) }
        startBreakTicker(breakUntilMs)
    }

    private fun startBreakTicker(breakUntilMs: Long?) {
        breakRunnable?.let { mainHandler.removeCallbacks(it) }
        breakRunnable = null
        if (breakUntilMs == null) return
        val breakText = overlayView?.findViewById<TextView>(R.id.block_break_text) ?: return
        breakText.visibility = View.VISIBLE
        breakRunnable = object : Runnable {
            override fun run() {
                val remaining = breakUntilMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    breakText.text = localizedContext.getString(R.string.overlay_break_ended)
                    coordinator.get().refresh()
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

    private fun updateBlockView(view: View, message: String, reason: String, breakUntilMs: Long?) {
        view.findViewById<TextView>(R.id.block_message).text = message
        view.findViewById<TextView>(R.id.block_reason).text = reason
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

    private fun addOverlay(view: View, focusable: Boolean) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (focusable) {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            } else {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            },
            PixelFormat.TRANSLUCENT,
        )
        overlayView = view
        overlayParams = params
        try {
            windowManager.addView(view, params)
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
