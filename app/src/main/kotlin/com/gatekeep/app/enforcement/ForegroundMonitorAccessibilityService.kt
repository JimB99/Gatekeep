package com.gatekeep.app.enforcement

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.gatekeep.app.util.EnforcementLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Not [@dagger.hilt.android.AndroidEntryPoint]: Hilt member injection is unreliable for
 * system-bound services under the instrumented [CustomTestApplication], same as [BootReceiver].
 */
class ForegroundMonitorAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val coordinator = coordinator() ?: return
        val eventType = event.eventType
        val packageName = event.packageName?.toString()
        val className = event.className?.toString()
        try {
            when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (packageName != null) {
                        coordinator.onForegroundAppChanged(packageName, className)
                    } else {
                        val snapshots = overlayWindowSnapshots()
                        if (snapshots.isNotEmpty()) {
                            coordinator.onTopWindowChanged(snapshots)
                        }
                    }
                }
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    coordinator.onWindowsLayoutChanged()
                }
            }
        } catch (e: Exception) {
            enforcementLog()?.logError("Accessibility event failed", e)
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
                    isOverlay = isOverlayWindow(window.type, pkg),
                    isFocused = window.isFocused,
                    isActive = window.isActive,
                    windowType = window.type,
                )
            }
        } catch (e: Exception) {
            enforcementLog()?.logError("Read accessibility windows failed", e)
            emptyList()
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val coordinator = coordinator() ?: return
        coordinator.onAccessibilityConnected()
        coordinator.refresh()
    }

    override fun onDestroy() {
        instance = null
        coordinator()?.onAccessibilityDisconnected()
        super.onDestroy()
    }

    private fun coordinator(): EnforcementCoordinator? = runCatching {
        EntryPointAccessors.fromApplication(
            applicationContext,
            AccessibilityMonitorEntryPoint::class.java,
        ).coordinator()
    }.getOrNull()

    private fun enforcementLog(): EnforcementLog? = runCatching {
        EntryPointAccessors.fromApplication(
            applicationContext,
            AccessibilityMonitorEntryPoint::class.java,
        ).enforcementLog()
    }.getOrNull()

    private fun isOverlayWindow(windowType: Int, windowPackage: String): Boolean {
        if (windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return true
        // TYPE_APPLICATION_OVERLAY is reported as TYPE_SYSTEM; skip our own overlay so Home
        // is not classified as the blocked Gatekeep activity still in front.
        return windowType == AccessibilityWindowInfo.TYPE_SYSTEM && windowPackage == packageName
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccessibilityMonitorEntryPoint {
        fun coordinator(): EnforcementCoordinator
        fun enforcementLog(): EnforcementLog
    }

    companion object {
        @Volatile
        var instance: ForegroundMonitorAccessibilityService? = null
    }
}
