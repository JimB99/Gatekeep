package com.gatekeep.app.enforcement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.gatekeep.app.R
import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.app.util.formatDurationMs
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
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var waitRunnable: Runnable? = null
    private var frictionInProgress = false
    private var currentChallenge: MathChallenge? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    fun isFrictionInProgress(): Boolean = frictionInProgress

    fun clearFrictionState() {
        frictionInProgress = false
        currentChallenge = null
    }

    @SuppressLint("InflateParams")
    fun show(
        packageName: String,
        message: String,
        reason: String,
        breakUntilMs: Long?,
        frictionMethod: FrictionMethod,
        difficulty: FrictionDifficulty,
        extensionMs: Long,
        waitDurationSeconds: Int = 60,
        profilePasswordHash: String? = null,
        onProfileUnlocked: (() -> Unit)? = null,
    ) {
        mainHandler.post {
            try {
                if (overlayView != null && frictionInProgress) return@post
                if (overlayView != null) {
                    updateBlock(message, reason, breakUntilMs)
                    return@post
                }
                createBlockOverlay(
                    packageName, message, reason, breakUntilMs,
                    frictionMethod, difficulty, extensionMs, waitDurationSeconds,
                    profilePasswordHash, onProfileUnlocked,
                )
            } catch (_: Exception) { }
        }
    }

    fun showDelay(delaySeconds: Int, message: String, onComplete: () -> Unit) {
        mainHandler.post {
            try {
                hide()
                val view = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)
                view.findViewById<TextView>(R.id.block_message).text = message
                view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE
                view.findViewById<Button>(R.id.block_back_btn).visibility = View.GONE
                val countdown = view.findViewById<TextView>(R.id.wait_countdown)
                countdown.visibility = View.VISIBLE
                var remaining = delaySeconds
                countdown.text = "${remaining}s"
                addOverlay(view, focusable = false)
                waitRunnable = object : Runnable {
                    override fun run() {
                        remaining--
                        if (remaining <= 0) {
                            hide()
                            onComplete()
                        } else {
                            countdown.text = "${remaining}s"
                            mainHandler.postDelayed(this, 1000)
                        }
                    }
                }
                mainHandler.postDelayed(waitRunnable!!, 1000)
            } catch (_: Exception) { }
        }
    }

    fun hide() {
        mainHandler.post {
            waitRunnable?.let { mainHandler.removeCallbacks(it) }
            waitRunnable = null
            frictionInProgress = false
            currentChallenge = null
            overlayView?.let {
                runCatching { windowManager.removeView(it) }
                overlayView = null
                overlayParams = null
            }
            coordinator.get().onBlockDismissed()
        }
    }

    @SuppressLint("InflateParams")
    private fun createBlockOverlay(
        packageName: String,
        message: String,
        reason: String,
        breakUntilMs: Long?,
        frictionMethod: FrictionMethod,
        difficulty: FrictionDifficulty,
        extensionMs: Long,
        waitDurationSeconds: Int,
        profilePasswordHash: String?,
        onProfileUnlocked: (() -> Unit)?,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)
        updateBlockView(view, message, reason, breakUntilMs)

        val challenge = if (frictionMethod == FrictionMethod.math) {
            FrictionChallenge.generate(difficulty).also { currentChallenge = it }
        } else null

        view.findViewById<Button>(R.id.block_back_btn).setOnClickListener {
            ForegroundMonitorAccessibilityService.instance?.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME,
            )
            hide()
        }

        addOverlay(view, focusable = false)

        if (reason == "profilePin" || (frictionMethod == FrictionMethod.password && profilePasswordHash != null)) {
            frictionInProgress = true
            showPasswordFriction(view, profilePasswordHash, packageName, extensionMs, onProfileUnlocked)
        } else {
            view.findViewById<Button>(R.id.block_continue_btn).setOnClickListener {
                showFriction(view, frictionMethod, challenge, packageName, extensionMs, waitDurationSeconds, profilePasswordHash)
            }
        }
    }

    private fun showFriction(
        view: View,
        method: FrictionMethod,
        challenge: MathChallenge?,
        packageName: String,
        extensionMs: Long,
        waitDurationSeconds: Int,
        profilePasswordHash: String?,
    ) {
        frictionInProgress = true
        makeOverlayFocusableForInput()

        val container = view.findViewById<LinearLayout>(R.id.friction_container)
        container.visibility = View.VISIBLE
        view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE

        when (method) {
            FrictionMethod.math -> {
                view.findViewById<TextView>(R.id.friction_prompt).text = challenge?.question ?: "Solve"
                val input = view.findViewById<EditText>(R.id.friction_input)
                input.visibility = View.VISIBLE
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
                    if (challenge != null && FrictionChallenge.verify(challenge, answer)) {
                        frictionInProgress = false
                        coordinator.get().grantExtension(packageName, extensionMs)
                    }
                }
                showKeyboard(input)
            }
            FrictionMethod.password -> showPasswordFriction(view, profilePasswordHash, packageName, extensionMs, null)
            FrictionMethod.waitOneMin -> {
                view.findViewById<TextView>(R.id.friction_prompt).text = "Wait to continue"
                val countdown = view.findViewById<TextView>(R.id.wait_countdown)
                countdown.visibility = View.VISIBLE
                var remaining = waitDurationSeconds
                countdown.text = "${remaining}s"
                waitRunnable = object : Runnable {
                    override fun run() {
                        remaining--
                        if (remaining <= 0) {
                            frictionInProgress = false
                            coordinator.get().grantExtension(packageName, extensionMs)
                        } else {
                            countdown.text = "${remaining}s"
                            mainHandler.postDelayed(this, 1000)
                        }
                    }
                }
                mainHandler.postDelayed(waitRunnable!!, 1000)
            }
            else -> {
                view.findViewById<TextView>(R.id.friction_prompt).text = "Use math, wait, or profile PIN in settings"
            }
        }
    }

    private fun showPasswordFriction(
        view: View,
        profilePasswordHash: String?,
        packageName: String,
        extensionMs: Long,
        onProfileUnlocked: (() -> Unit)?,
    ) {
        makeOverlayFocusableForInput()
        val container = view.findViewById<LinearLayout>(R.id.friction_container)
        container.visibility = View.VISIBLE
        view.findViewById<Button>(R.id.block_continue_btn).visibility = View.GONE
        view.findViewById<TextView>(R.id.friction_prompt).text = "Enter profile PIN"
        val input = view.findViewById<EditText>(R.id.friction_input)
        input.visibility = View.VISIBLE
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val submit = view.findViewById<Button>(R.id.friction_submit)
        submit.visibility = View.VISIBLE
        submit.text = "Unlock"
        submit.setOnClickListener {
            val pin = input.text.toString()
            if (profilePasswordHash != null && PasswordHasher.verify(pin, profilePasswordHash)) {
                frictionInProgress = false
                onProfileUnlocked?.invoke()
                if (extensionMs > 0) {
                    coordinator.get().grantExtension(packageName, extensionMs)
                } else {
                    coordinator.get().onBlockDismissed()
                    hide()
                    coordinator.get().refresh()
                }
            }
        }
        showKeyboard(input)
    }

    private fun makeOverlayFocusableForInput() {
        overlayParams?.let { params ->
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            overlayView?.let { windowManager.updateViewLayout(it, params) }
        }
        overlayView?.requestFocus()
    }

    private fun showKeyboard(input: EditText) {
        input.requestFocus()
        mainHandler.postDelayed({
            inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun updateBlock(message: String, reason: String, breakUntilMs: Long?) {
        if (frictionInProgress) return
        overlayView?.let { updateBlockView(it, message, reason, breakUntilMs) }
    }

    private fun updateBlockView(view: View, message: String, reason: String, breakUntilMs: Long?) {
        view.findViewById<TextView>(R.id.block_message).text = message
        view.findViewById<TextView>(R.id.block_reason).text = reason
        val breakText = view.findViewById<TextView>(R.id.block_break_text)
        if (breakUntilMs != null) {
            breakText.visibility = View.VISIBLE
            breakText.text = "Break ends in ${formatDurationMs(breakUntilMs - System.currentTimeMillis())}"
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
        windowManager.addView(view, params)
    }
}
