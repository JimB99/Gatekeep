package com.gatekeep.app.enforcement

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devicePauseStore: DevicePauseStore,
) {
    private var registered = false
    private val pauseTracker = ScreenPauseTracker { SystemClock.elapsedRealtime() }
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private var onPausedListener: (() -> Unit)? = null
    private var onBecameUsingListener: (() -> Unit)? = null
    private var deviceUsePhase: DeviceUsePhase = DeviceUsePhase.Using
    private var keyguardLockedListener: KeyguardManager.KeyguardLockedStateListener? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    onScreenOff()
                    emitDeviceUsePhase()
                }
                Intent.ACTION_SCREEN_ON -> {
                    onScreenOn()
                    emitDeviceUsePhase()
                }
                Intent.ACTION_USER_PRESENT -> emitDeviceUsePhase()
            }
        }
    }

    fun register(
        onPaused: (() -> Unit)? = null,
        onBecameUsing: (() -> Unit)? = null,
    ) {
        onPausedListener = onPaused
        onBecameUsingListener = onBecameUsing
        if (registered) {
            emitDeviceUsePhase()
            return
        }
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
        registerKeyguardListener()
        registered = true
        emitDeviceUsePhase()
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        unregisterKeyguardListener()
        registered = false
        onPausedListener = null
        onBecameUsingListener = null
    }

    fun isScreenOn(): Boolean = currentScreenOn()

    fun isKeyguardLocked(): Boolean = keyguardManager?.isKeyguardLocked == true

    fun persistedPausedAtEpochMs(): Long? = devicePauseStore.pausedAtEpochMs()

    fun clearPersistedPause() {
        devicePauseStore.clear()
    }

    fun deviceUsePhase(hasLeftApp: Boolean): DeviceUsePhase =
        DeviceUsePolicy.phase(
            screenOn = currentScreenOn(),
            keyguardLocked = isKeyguardLocked(),
            hasLeftApp = hasLeftApp,
        )

    private fun currentScreenOn(): Boolean {
        if (isDisplayOff()) {
            if (pauseTracker.screenOn) onScreenOff()
            return false
        }
        return pauseTracker.screenOn
    }

    private fun emitDeviceUsePhase() {
        val next = DeviceUsePolicy.phase(
            screenOn = currentScreenOn(),
            keyguardLocked = isKeyguardLocked(),
            hasLeftApp = false,
        )
        val previous = deviceUsePhase
        if (next == DeviceUsePhase.Paused) {
            devicePauseStore.setPausedAt(System.currentTimeMillis())
        }
        if (previous == next) return
        deviceUsePhase = next
        when {
            DeviceUsePolicy.shouldEvaluateOnResume(previous, next) -> onBecameUsingListener?.invoke()
            next == DeviceUsePhase.Paused -> onPausedListener?.invoke()
        }
    }

    private fun registerKeyguardListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return
        val listener = KeyguardManager.KeyguardLockedStateListener { emitDeviceUsePhase() }
        keyguardLockedListener = listener
        runCatching {
            keyguardManager?.addKeyguardLockedStateListener(context.mainExecutor, listener)
        }
    }

    private fun unregisterKeyguardListener() {
        val listener = keyguardLockedListener ?: return
        keyguardLockedListener = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return
        runCatching { keyguardManager?.removeKeyguardLockedStateListener(listener) }
    }

    private fun isDisplayOff(): Boolean {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null && display.state == Display.STATE_OFF) return true
        return powerManager?.isInteractive == false
    }

    fun onScreenOff() {
        pauseTracker.onScreenOff()
    }

    fun onScreenOn() {
        pauseTracker.onScreenOn()
    }

    fun pausedElapsedMs(): Long = pauseTracker.pausedElapsedMs()

    fun resetPauseTracking() {
        pauseTracker.reset()
    }

    internal fun createStopwatch(): PausedStopwatch = pauseTracker.createStopwatch()
}

/**
 * Screen-off accounting shared by [ScreenStateMonitor] and wait countdowns.
 * Elapsed time does not advance while the screen is off.
 */
internal class ScreenPauseTracker(
    private val elapsedNow: () -> Long,
) {
    var screenOn: Boolean = true
        private set
    private var screenOffAtElapsed: Long? = null
    private var accumulatedOffMs: Long = 0

    fun onScreenOff() {
        if (!screenOn) return
        screenOn = false
        screenOffAtElapsed = elapsedNow()
    }

    /** @return how long the screen was off, or 0 if it was already on. */
    fun onScreenOn(): Long {
        val offAt = screenOffAtElapsed
        val duration = if (offAt != null) (elapsedNow() - offAt).coerceAtLeast(0) else 0L
        if (offAt != null) accumulatedOffMs += duration
        screenOffAtElapsed = null
        screenOn = true
        return duration
    }

    fun pausedElapsedMs(): Long {
        val offAt = screenOffAtElapsed ?: return accumulatedOffMs
        return accumulatedOffMs + (elapsedNow() - offAt).coerceAtLeast(0)
    }

    fun reset() {
        accumulatedOffMs = 0
        screenOffAtElapsed = null
        screenOn = true
    }

    fun createStopwatch(): PausedStopwatch = PausedStopwatch(this, elapsedNow)
}

internal class PausedStopwatch(
    private val tracker: ScreenPauseTracker,
    private val elapsedNow: () -> Long,
) {
    private val startElapsed = elapsedNow()
    private val pausedAtStart = tracker.pausedElapsedMs()

    fun elapsedMs(): Long {
        val paused = (tracker.pausedElapsedMs() - pausedAtStart).coerceAtLeast(0)
        return (elapsedNow() - startElapsed - paused).coerceAtLeast(0)
    }

    fun remainingMs(totalMs: Long): Long =
        (totalMs - elapsedMs()).coerceAtLeast(0)
}
