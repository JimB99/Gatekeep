package com.gatekeep.app.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var registered = false
    @Volatile private var screenOn = true
    @Volatile private var screenOffAtElapsed: Long? = null
    @Volatile private var accumulatedOffMs: Long = 0
    private var screenOffWallStartMs: Long? = null
  private var onScreenOnListener: ((Long) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onScreenOn()
            }
        }
    }

    fun register(onScreenOn: ((screenOffDurationMs: Long) -> Unit)? = null) {
        onScreenOnListener = onScreenOn
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
        onScreenOnListener = null
    }

    fun isScreenOn(): Boolean = screenOn

    fun onScreenOff() {
        if (!screenOn) return
        screenOn = false
        screenOffAtElapsed = SystemClock.elapsedRealtime()
        screenOffWallStartMs = System.currentTimeMillis()
    }

    fun onScreenOn() {
        val offWallStart = screenOffWallStartMs
        val offDuration = offWallStart?.let { System.currentTimeMillis() - it } ?: 0L
        screenOffAtElapsed?.let { offAt ->
            accumulatedOffMs += SystemClock.elapsedRealtime() - offAt
        }
        screenOffAtElapsed = null
        screenOffWallStartMs = null
        screenOn = true
        if (offDuration > 0) {
            onScreenOnListener?.invoke(offDuration)
        }
    }

    fun pausedElapsedMs(): Long {
        val base = accumulatedOffMs
        val offAt = screenOffAtElapsed ?: return base
        return base + (SystemClock.elapsedRealtime() - offAt)
    }

    fun resetPauseTracking() {
        accumulatedOffMs = 0
        screenOffAtElapsed = null
        screenOffWallStartMs = null
    }

    fun createStopwatch(): PausedStopwatch = PausedStopwatch(this)

    class PausedStopwatch(private val monitor: ScreenStateMonitor) {
        private val startElapsed = SystemClock.elapsedRealtime()
        private var localPausedMs = 0L
        private var pausedAt: Long? = null

        init {
            if (!monitor.isScreenOn()) {
                pausedAt = SystemClock.elapsedRealtime()
            }
        }

        fun onScreenOff() {
            if (pausedAt == null) pausedAt = SystemClock.elapsedRealtime()
        }

        fun onScreenOn() {
            pausedAt?.let {
                localPausedMs += SystemClock.elapsedRealtime() - it
                pausedAt = null
            }
        }

        fun elapsedMs(): Long {
            val extra = pausedAt?.let { SystemClock.elapsedRealtime() - it } ?: 0L
            return (SystemClock.elapsedRealtime() - startElapsed - localPausedMs - extra).coerceAtLeast(0)
        }

        fun remainingMs(totalMs: Long): Long =
            (totalMs - elapsedMs()).coerceAtLeast(0)
    }
}
