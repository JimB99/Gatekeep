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
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return
                    val className = event.className?.toString()
                    coordinator.onForegroundAppChanged(packageName, className)
                }
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return
                    // Overlay windows emit package-level windows-changed events without
                    // activity class; real navigation is reported via WINDOW_STATE_CHANGED.
                    if (packageName == applicationContext.packageName) return
                    coordinator.onForegroundAppChanged(packageName, windowClassName = null)
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
