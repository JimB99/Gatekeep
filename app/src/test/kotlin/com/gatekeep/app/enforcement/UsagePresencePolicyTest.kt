package com.gatekeep.app.enforcement

import com.gatekeep.app.util.ForegroundEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePresencePolicyTest {

    @Test
    fun unknownEvents_countAsStillForeground() {
        assertTrue(UsagePresencePolicy.isStillForeground(null))
    }

    @Test
    fun resumed_isStillForeground() {
        assertTrue(UsagePresencePolicy.isStillForeground(ForegroundEventType.RESUMED))
    }

    @Test
    fun paused_whileAccessibilityStillFocused_isStillForeground() {
        assertTrue(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = true,
                topWindowKind = OverlayWindowKind.BlockedApp,
            ),
        )
    }

    @Test
    fun paused_whileShadeOnTop_isStillForeground() {
        assertTrue(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = OverlayWindowKind.Shade,
            ),
        )
    }

    @Test
    fun paused_whileTransientSystemUiOnTop_isStillForeground() {
        assertTrue(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = OverlayWindowKind.TransientSystemUi,
            ),
        )
    }

    @Test
    fun paused_whileUnknownSystemUiOnTop_isStillForeground() {
        assertTrue(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = OverlayWindowKind.Unknown,
            ),
        )
    }

    @Test
    fun paused_whileLauncherInFront_isALeave() {
        assertFalse(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = OverlayWindowKind.Launcher,
            ),
        )
    }

    @Test
    fun paused_whileOtherAppInFront_isALeave() {
        assertFalse(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = OverlayWindowKind.OtherApp,
            ),
        )
    }

    @Test
    fun paused_whileRecentsInFront_isALeave() {
        assertFalse(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = OverlayWindowKind.Recents,
            ),
        )
    }

    @Test
    fun paused_whileIncomingCallInFront_isALeave() {
        assertFalse(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = OverlayWindowKind.IncomingCall,
            ),
        )
    }

    @Test
    fun paused_withoutWindowEvidence_isALeave() {
        assertFalse(
            UsagePresencePolicy.isStillForeground(
                lastEventType = ForegroundEventType.PAUSED,
                accessibilityFocusedOnMonitoredApp = false,
                topWindowKind = null,
            ),
        )
    }
}
