package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayWindowClassifierTest {

    private val blocked = "com.instagram.android"
    private val ime = OverlayWindowClassifier.imePackages

    @Test
    fun classify_table() {
        val rows = listOf(
            Triple(
                "com.android.systemui",
                "com.android.systemui.volume.VolumeDialog",
                OverlayWindowKind.TransientSystemUi,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.volume.VolumeDialogImpl",
                OverlayWindowKind.TransientSystemUi,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.settings.brightness.BrightnessDialog",
                OverlayWindowKind.TransientSystemUi,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.globalactions.GlobalActionsDialog",
                OverlayWindowKind.TransientSystemUi,
            ),
            Triple(
                "com.android.systemui",
                "android.widget.Toast",
                OverlayWindowKind.TransientSystemUi,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.screenshot.ScreenshotShelfView",
                OverlayWindowKind.TransientSystemUi,
            ),
            Triple(
                ime.first(),
                "com.google.android.inputmethod.latin.LatinIME",
                OverlayWindowKind.TransientSystemUi,
            ),
            Triple(
                "com.android.systemui",
                "com.android.quickstep.RecentsActivity",
                OverlayWindowKind.Recents,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.recents.RecentsView",
                OverlayWindowKind.Recents,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.recents.TaskSwitcher",
                OverlayWindowKind.Recents,
            ),
            Triple(
                "com.google.android.apps.nexuslauncher",
                "com.android.quickstep.RecentsActivity",
                OverlayWindowKind.Recents,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.statusbar.NotificationShadeWindowView",
                OverlayWindowKind.Shade,
            ),
            Triple(
                "com.android.incallui",
                "com.android.incallui.InCallActivity",
                OverlayWindowKind.IncomingCall,
            ),
            Triple(
                "com.google.android.dialer",
                "com.android.incallui.InCallActivity",
                OverlayWindowKind.IncomingCall,
            ),
            Triple(
                "com.android.launcher3",
                "com.android.launcher3.Launcher",
                OverlayWindowKind.Launcher,
            ),
            Triple(
                blocked,
                "com.instagram.android.activity.MainTabActivity",
                OverlayWindowKind.BlockedApp,
            ),
            Triple(
                "com.example.other",
                "com.example.other.MainActivity",
                OverlayWindowKind.OtherApp,
            ),
            Triple(
                "com.android.systemui",
                "com.android.systemui.SomethingUnknown",
                OverlayWindowKind.Unknown,
            ),
        )
        for ((pkg, cls, expected) in rows) {
            assertEquals(
                "$pkg $cls",
                expected,
                OverlayWindowClassifier.classify(pkg, cls, blocked),
            )
        }
    }

    @Test
    fun pickTopRelevantWindow_skipsOverlayAndVolume() {
        val overlayOwner = "com.gatekeep.app"
        val windows = listOf(
            OverlayWindowSnapshot(overlayOwner, "android.widget.FrameLayout", isOverlay = true),
            OverlayWindowSnapshot(
                "com.android.systemui",
                "com.android.systemui.volume.VolumeDialog",
                isOverlay = false,
            ),
            OverlayWindowSnapshot(blocked, "com.instagram.android.MainActivity", isOverlay = false),
        )
        val top = OverlayWindowClassifier.pickTopRelevantWindow(windows, blocked, overlayOwner)
        assertEquals(blocked, top?.packageName)
    }

    @Test
    fun pickTopRelevantWindow_recentsStaysOnTop() {
        val windows = listOf(
            OverlayWindowSnapshot(
                "com.android.systemui",
                "com.android.quickstep.RecentsActivity",
                isOverlay = false,
            ),
            OverlayWindowSnapshot(blocked, "com.instagram.android.MainActivity", isOverlay = false),
        )
        val top = OverlayWindowClassifier.pickTopRelevantWindow(windows, blocked, "com.gatekeep.app")
        assertEquals("com.android.systemui", top?.packageName)
        assertEquals(
            OverlayWindowKind.Recents,
            OverlayWindowClassifier.classify(top!!.packageName, top.className, blocked),
        )
    }
}
