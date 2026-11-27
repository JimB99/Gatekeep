package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockSkipLeftoverBlockPolicyTest {

    private val youtube = "app.revanced.android.youtube"
    private val launcher = "com.sec.android.app.launcher"

    @Test
    fun unlockSkipOnHome_clearsHiddenLeftoverForLastMonitored() {
        assertTrue(
            UnlockSkipLeftoverBlockPolicy.shouldClearLeftoverBlock(
                presentation = BlockPresentation.HiddenForOtherApp(youtube, 1L),
                lastMonitoredPackage = youtube,
                currentForegroundPackage = launcher,
                topWindowKind = OverlayWindowKind.Launcher,
            ),
        )
    }

    @Test
    fun unlockSkipOnHome_clearsVisibleLeftoverForLastMonitored() {
        assertTrue(
            UnlockSkipLeftoverBlockPolicy.shouldClearLeftoverBlock(
                presentation = BlockPresentation.Visible(youtube, 1L),
                lastMonitoredPackage = youtube,
                currentForegroundPackage = launcher,
                topWindowKind = OverlayWindowKind.Launcher,
            ),
        )
    }

    @Test
    fun unlockOnMonitoredApp_doesNotClear() {
        assertFalse(
            UnlockSkipLeftoverBlockPolicy.shouldClearLeftoverBlock(
                presentation = BlockPresentation.HiddenForOtherApp(youtube, 1L),
                lastMonitoredPackage = youtube,
                currentForegroundPackage = youtube,
                topWindowKind = OverlayWindowKind.BlockedApp,
            ),
        )
    }

    @Test
    fun noBlock_doesNotClear() {
        assertFalse(
            UnlockSkipLeftoverBlockPolicy.shouldClearLeftoverBlock(
                presentation = BlockPresentation.None,
                lastMonitoredPackage = youtube,
                currentForegroundPackage = launcher,
                topWindowKind = OverlayWindowKind.Launcher,
            ),
        )
    }
}
