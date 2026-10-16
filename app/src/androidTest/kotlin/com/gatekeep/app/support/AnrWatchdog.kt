package com.gatekeep.app.support

import android.app.Instrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * ANR dialogs freeze UiAutomator (especially [UiDevice.waitForIdle]). Never tap Wait —
 * that parks the suite until the next ANR cycle.
 *
 * Do not `am force-stop com.gatekeep.app` from the device: instrumented tests run in
 * that process, so force-stop kills the suite (seen as Process crashed).
 */
object AnrWatchdog {
    private const val POLL_MS = 2_000L
    private val started = AtomicBoolean(false)
    val seen = AtomicBoolean(false)

    fun configureUiAutomatorTimeouts() {
        Configurator.getInstance().apply {
            waitForIdleTimeout = 1_000
            waitForSelectorTimeout = 2_000
            actionAcknowledgmentTimeout = 1_000
            scrollAcknowledgmentTimeout = 1_000
        }
    }

    fun start(instrumentation: Instrumentation) {
        if (!started.compareAndSet(false, true)) return
        configureUiAutomatorTimeouts()
        thread(name = "gatekeep-anr-watchdog", isDaemon = true) {
            val device = UiDevice.getInstance(instrumentation)
            while (!Thread.currentThread().isInterrupted) {
                runCatching {
                    if (anrDialogPresent(device)) {
                        seen.set(true)
                        dismissCloseAppNeverWait(device)
                    }
                }
                Thread.sleep(POLL_MS)
            }
        }
    }

    fun anrDialogPresent(device: UiDevice): Boolean {
        return runCatching {
            device.hasObject(By.res("android", "aerr_close")) ||
                device.hasObject(By.res("android", "aerr_wait")) ||
                device.hasObject(By.textContains("isn't responding"))
        }.getOrDefault(false)
    }

    fun dismissCloseAppNeverWait(device: UiDevice): Boolean {
        val close = runCatching {
            device.findObject(By.res("android", "aerr_close"))
                ?: device.findObject(By.text("Close app"))
                ?: device.findObject(By.text("Close App"))
                ?: device.findObject(By.text("App schließen"))
                ?: device.findObject(By.textContains("Cerrar"))
        }.getOrNull()
        if (close != null) {
            runCatching { close.click() }
            return true
        }
        // Last resort: Back/Escape. Never tap the right-side Wait button.
        runCatching { device.pressBack() }
        return true
    }

    fun failIfSeen(where: String) {
        if (!seen.getAndSet(false)) return
        throw AssertionError(
            "ANR dialog at $where; dismissed Close app (never Wait) so the suite does not hang",
        )
    }
}
