package com.gatekeep.app.enforcement

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.gatekeep.app.util.EnforcementLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundMonitorAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: EnforcementCoordinator
    @Inject lateinit var enforcementLog: EnforcementLog

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                -> {
                    val packageName = event.packageName?.toString() ?: return
                    if (packageName == this.packageName) return
                    coordinator.onForegroundAppChanged(packageName)
                    coordinator.pollForeground()
                }
            }
        } catch (e: Exception) {
            enforcementLog.logError("Accessibility event failed", e)
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        coordinator.onAccessibilityConnected()
        coordinator.refresh()
    }

    override fun onDestroy() {
        instance = null
        coordinator.onAccessibilityDisconnected()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: ForegroundMonitorAccessibilityService? = null
    }
}
