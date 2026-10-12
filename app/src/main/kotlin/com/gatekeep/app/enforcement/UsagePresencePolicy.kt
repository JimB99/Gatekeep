package com.gatekeep.app.enforcement

import com.gatekeep.app.util.ForegroundEventType

/**
 * UsageStats ACTIVITY_PAUSED / MOVE_TO_BACKGROUND is not a leave by itself.
 * Shade, heads-up, and similar overlays pause the activity while the monitored app
 * is still the real foreground. A leave is Home, Recents, another app, or an incoming call.
 * Pause with no window evidence keeps the existing home-without-other-app path.
 */
object UsagePresencePolicy {

    fun isStillForeground(
        lastEventType: ForegroundEventType?,
        accessibilityFocusedOnMonitoredApp: Boolean = false,
        topWindowKind: OverlayWindowKind? = null,
    ): Boolean {
        if (lastEventType == null) return true
        if (lastEventType == ForegroundEventType.RESUMED) return true
        if (accessibilityFocusedOnMonitoredApp) return true
        return when (topWindowKind) {
            OverlayWindowKind.Shade,
            OverlayWindowKind.TransientSystemUi,
            OverlayWindowKind.Unknown,
            OverlayWindowKind.BlockedApp,
            -> true
            OverlayWindowKind.Launcher,
            OverlayWindowKind.Recents,
            OverlayWindowKind.OtherApp,
            OverlayWindowKind.IncomingCall,
            null,
            -> false
        }
    }
}
