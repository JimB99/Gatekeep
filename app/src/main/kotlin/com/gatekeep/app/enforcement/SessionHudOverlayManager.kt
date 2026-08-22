package com.gatekeep.app.enforcement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatekeep.app.util.formatDurationMs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionHudOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hudView: ComposeView? = null
    private var currentData: HudData? = null

    data class HudData(
        val appLabel: String,
        val remainingSessionMs: Long?,
        val remainingDailyMs: Long?,
        val opacity: Float,
    )

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        appLabel: String,
        remainingSessionMs: Long?,
        remainingDailyMs: Long?,
        opacity: Float,
    ) {
        val data = HudData(appLabel, remainingSessionMs, remainingDailyMs, opacity)
        if (currentData == data && hudView != null) return
        currentData = data

        mainHandler.post {
            if (hudView == null) {
                val composeView = ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setViewTreeLifecycleOwner(ProcessLifecycleOwner.get())
                }
                hudView = composeView
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                windowManager.addView(composeView, params)
            }
            hudView?.setContent {
                SessionHudBar(data)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            hudView?.let {
                runCatching { windowManager.removeView(it) }
                hudView = null
                currentData = null
            }
        }
    }
}

@Composable
private fun SessionHudBar(data: SessionHudOverlayManager.HudData) {
    val sessionTotal = data.remainingSessionMs?.let { it + 1 } ?: 1L
    val sessionProgress = data.remainingSessionMs?.toFloat()?.div(sessionTotal.coerceAtLeast(1)) ?: 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A73E8).copy(alpha = data.opacity))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.appLabel,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = buildString {
                    append(formatDurationMs(data.remainingDailyMs))
                    append(" left today")
                    data.remainingSessionMs?.let { append(" · ${formatDurationMs(it)} session") }
                },
                color = Color.White,
                fontSize = 11.sp,
            )
        }
        LinearProgressIndicator(
            progress = { sessionProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
        )
    }
}
