package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unlock must not show a wait overlay until the current app is confirmed monitored.
 * Samsung log: lastMonitored stayed YouTube while the launcher was actually in front.
 */
class UnlockOverlayPolicyTest {

    private val youtube = "app.revanced.android.youtube"
    private val launcher = "com.sec.android.app.launcher"
    private val whatsapp = "com.whatsapp"
    private val dialer = "com.samsung.android.dialer"
    private val owner = "com.gatekeep.app"
    private val monitored = setOf(youtube)
    private val ignored = setOf("com.android.systemui")

    private fun target(
        current: String?,
        lastMonitored: String? = youtube,
        usage: String? = youtube,
        topPackage: String? = null,
        topKind: OverlayWindowKind? = null,
    ): String? = UnlockOverlayPolicy.resumeTarget(
        currentForegroundPackage = current,
        lastMonitoredPackage = lastMonitored,
        usageFallbackPackage = usage,
        topWindowPackage = topPackage,
        topWindowKind = topKind,
        monitoredPackages = monitored,
        ignoredPackages = ignored,
        overlayOwnerPackage = owner,
    )

    @Test
    fun unlockToLauncher_doesNotResumeLastMonitoredYoutube() {
        assertNull(
            target(
                current = youtube,
                topPackage = launcher,
                topKind = OverlayWindowKind.Launcher,
            ),
        )
    }

    @Test
    fun unlockAfterHomeCommitted_doesNotPreferLastMonitored() {
        assertNull(
            target(
                current = launcher,
                usage = youtube,
                topPackage = launcher,
                topKind = OverlayWindowKind.Launcher,
            ),
        )
    }

    @Test
    fun unlockToUntrackedApp_doesNotShowOverlay() {
        assertNull(
            target(
                current = whatsapp,
                usage = whatsapp,
                topPackage = whatsapp,
                topKind = OverlayWindowKind.OtherApp,
            ),
        )
        assertNull(
            target(
                current = dialer,
                usage = dialer,
                topPackage = dialer,
                topKind = OverlayWindowKind.OtherApp,
            ),
        )
    }

    @Test
    fun unlockIntoMonitoredApp_windowsConfirm_resumes() {
        assertEquals(
            youtube,
            target(
                current = youtube,
                topPackage = youtube,
                topKind = OverlayWindowKind.OtherApp,
            ),
        )
    }

    @Test
    fun unlockIntoMonitoredApp_a11ySilent_usesCurrent() {
        assertEquals(
            youtube,
            target(current = youtube, usage = youtube, topPackage = null, topKind = null),
        )
    }

    @Test
    fun unlock_a11ySilent_staleUsageDoesNotOverrideLauncherCurrent() {
        assertNull(
            target(current = launcher, usage = youtube, topPackage = null, topKind = null),
        )
    }

    @Test
    fun unlock_unknownForeground_fallsBackToLastMonitored() {
        assertEquals(
            youtube,
            target(current = null, lastMonitored = youtube, usage = null, topPackage = null),
        )
    }

    @Test
    fun unlock_systemuiUnknown_fallsBackToLastMonitored() {
        assertEquals(
            youtube,
            target(
                current = "com.android.systemui",
                lastMonitored = youtube,
                usage = youtube,
                topPackage = "com.android.systemui",
                topKind = OverlayWindowKind.Unknown,
            ),
        )
    }

    @Test
    fun pollMustNotRestoreHiddenBlock_whenWindowsUnknown() {
        assertFalse(
            UnlockOverlayPolicy.shouldRestoreHiddenBlockFromPoll(
                usagePackage = youtube,
                blockedPackage = youtube,
                topWindowPackage = null,
                topWindowKind = null,
            ),
        )
    }

    @Test
    fun pollMustNotRestoreHiddenBlock_whenLauncherOnTop() {
        assertFalse(
            UnlockOverlayPolicy.shouldRestoreHiddenBlockFromPoll(
                usagePackage = youtube,
                blockedPackage = youtube,
                topWindowPackage = launcher,
                topWindowKind = OverlayWindowKind.Launcher,
            ),
        )
    }

    @Test
    fun pollRestoresHiddenBlock_whenBlockedAppIsTop() {
        assertTrue(
            UnlockOverlayPolicy.shouldRestoreHiddenBlockFromPoll(
                usagePackage = youtube,
                blockedPackage = youtube,
                topWindowPackage = youtube,
                topWindowKind = OverlayWindowKind.OtherApp,
            ),
        )
    }
}
