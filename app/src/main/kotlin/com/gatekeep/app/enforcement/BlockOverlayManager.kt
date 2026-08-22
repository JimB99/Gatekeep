package com.gatekeep.app.enforcement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.app.util.formatDurationMs
import com.gatekeep.domain.FrictionChallenge
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: dagger.Lazy<EnforcementCoordinator>,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: ComposeView? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        packageName: String,
        message: String,
        reason: String,
        breakUntilMs: Long?,
        profile: Profile,
        limit: AppLimit?,
    ) {
        mainHandler.post {
            if (overlayView != null) return@post
            val frictionMethod = limit?.frictionMethod ?: profile.defaultFrictionMethod
            val difficulty = limit?.frictionDifficulty ?: profile.defaultFrictionDifficulty
            val extensionMs = limit?.extensionMsOnBypass ?: 5 * 60_000L

            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(ProcessLifecycleOwner.get())
                setContent {
                    BlockOverlayContent(
                        message = message,
                        reason = reason,
                        breakUntilMs = breakUntilMs,
                        frictionMethod = frictionMethod,
                        difficulty = difficulty,
                        profilePasswordHash = profile.passwordHash,
                        onBypass = {
                            coordinator.get().grantExtension(packageName, extensionMs)
                        },
                        onDismiss = { hide() },
                    )
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.CENTER }

            overlayView = composeView
            windowManager.addView(composeView, params)
        }
    }

    fun showDelay(packageName: String, delaySeconds: Int, message: String) {
        mainHandler.post {
            if (overlayView != null) return@post
            var remaining = delaySeconds
            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(ProcessLifecycleOwner.get())
                setContent {
                    var countdown by remember { mutableIntStateOf(remaining) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xE6000000)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Text("${countdown}s", color = Color.White)
                    }
                }
            }
            val params = overlayParams()
            overlayView = composeView
            windowManager.addView(composeView, params)

            val runnable = object : Runnable {
                override fun run() {
                    remaining--
                    if (remaining <= 0) {
                        hide()
                        coordinator.get().refresh()
                    } else {
                        mainHandler.postDelayed(this, 1000)
                    }
                }
            }
            mainHandler.postDelayed(runnable, 1000)
        }
    }

    fun hide() {
        mainHandler.post {
            overlayView?.let {
                runCatching { windowManager.removeView(it) }
                overlayView = null
            }
        }
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )
}

@Composable
private fun BlockOverlayContent(
    message: String,
    reason: String,
    breakUntilMs: Long?,
    frictionMethod: FrictionMethod,
    difficulty: FrictionDifficulty,
    profilePasswordHash: String?,
    onBypass: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showFriction by remember { mutableStateOf(false) }
    val challenge = remember { FrictionChallenge.generate(difficulty) }
    var answer by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var holdProgress by remember { mutableLongStateOf(0L) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0121212))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Gatekeep", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White, textAlign = TextAlign.Center)
        Text(reason, color = Color.Gray, textAlign = TextAlign.Center)
        if (breakUntilMs != null) {
            val remaining = (breakUntilMs - System.currentTimeMillis()).coerceAtLeast(0)
            Text("Break ends in ${formatDurationMs(remaining)}", color = Color(0xFF64B5F6))
        }
        Spacer(Modifier.height(24.dp))
        if (!showFriction) {
            Button(onClick = { showFriction = true }) { Text("Continue anyway") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss) { Text("Go back") }
        } else {
            when (frictionMethod) {
                FrictionMethod.math -> {
                    Text(challenge.question, color = Color.White)
                    OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer") })
                    Button(
                        onClick = {
                            if (FrictionChallenge.verify(challenge, answer.toIntOrNull() ?: -1)) onBypass()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Submit") }
                }
                FrictionMethod.typePhrase -> {
                    OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text("Type phrase") })
                    Button(
                        onClick = {
                            if (phrase == FrictionChallenge.DEFAULT_PHRASE) onBypass()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Submit") }
                }
                FrictionMethod.holdButton -> {
                    Text("Hold for ${FrictionChallenge.HOLD_BUTTON_SECONDS}s", color = Color.White)
                    Button(
                        onClick = { holdProgress += 1000 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Hold (${holdProgress / 1000}s)") }
                    if (holdProgress >= FrictionChallenge.HOLD_BUTTON_SECONDS * 1000L) onBypass()
                }
                FrictionMethod.password -> {
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") })
                    Button(
                        onClick = {
                            if (profilePasswordHash != null && PasswordHasher.verify(password, profilePasswordHash)) {
                                onBypass()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Unlock") }
                }
            }
        }
    }
}
