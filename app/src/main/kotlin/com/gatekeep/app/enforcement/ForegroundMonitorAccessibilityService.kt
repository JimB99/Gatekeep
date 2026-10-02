package com.gatekeep.app.enforcement

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.gatekeep.app.util.EnforcementLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundMonitorAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: EnforcementCoordinator
    @Inject lateinit var enforcementLog: EnforcementLog

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        val packageName = event.packageName?.toString()
        val className = event.className?.toString()
        try {
            when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (packageName != null) {
                        coordinator.onForegroundAppChanged(packageName, className)
                    }
                    val snapshots = overlayWindowSnapshots()
                    if (snapshots.isNotEmpty()) {
                        coordinator.onTopWindowChanged(snapshots)
                    }
                }
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    coordinator.onWindowsLayoutChanged()
                }
            }
        } catch (e: Exception) {
            enforcementLog.logError("Accessibility event failed", e)
        }
    }

    fun overlayWindowSnapshots(): List<OverlayWindowSnapshot> {
        return try {
            (windows ?: emptyList()).mapNotNull { window ->
                val root = runCatching { window.root }.getOrNull()
                val pkg = root?.packageName?.toString() ?: return@mapNotNull null
                OverlayWindowSnapshot(
                    packageName = pkg,
                    className = root.className?.toString(),
                    isOverlay = window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                )
            }
        } catch (e: Exception) {
            enforcementLog.logError("Read accessibility windows failed", e)
            emptyList()
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
