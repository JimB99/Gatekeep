package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRoutingPolicyTest {

    private val blockedPkg = "com.instagram.android"
    private val launcher = "com.android.launcher3"
    private val systemUi = "com.android.systemui"
    private val otherApp = "com.example.other"
    private val visible = BlockPresentation.Visible(blockedPkg, 1L)
    private val hidden = BlockPresentation.HiddenForOtherApp(blockedPkg, 1L)

    private fun decide(
        incoming: String,
        current: String? = blockedPkg,
        blocked: String? = blockedPkg,
        blocking: Boolean = true,
        entered: Long = 0L,
        now: Long = 10_000L,
        presentation: BlockPresentation = visible,
        kind: OverlayWindowKind,
    ) = ForegroundRoutingPolicy.decide(
        incomingPackage = incoming,
        currentForegroundPackage = current,
        blockedPackage = blocked,
        blockingActive = blocking,
        blockEnteredAtMs = entered,
        nowMs = now,
        presentation = presentation,
        kind = kind,
    )

    @Test
    fun decide_homeWhileBlocked_hideNow() {
        val now = 10_000L
        val entered = now - ForegroundRoutingPolicy.BLOCK_STABILIZATION_MS
        val route = decide(
            incoming = launcher,
            entered = entered,
            now = now,
            kind = OverlayWindowKind.Launcher,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
        assertEquals(launcher, (route as ForegroundRoutingPolicy.Route.HideNow).packageName)
    }

    @Test
    fun decide_overviewWhileBlocked_hideNow() {
        val route = decide(incoming = systemUi, kind = OverlayWindowKind.Recents)
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
    }

    @Test
    fun decide_volumeWhileBlocked_ignore() {
        val route = decide(incoming = systemUi, kind = OverlayWindowKind.TransientSystemUi)
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_unknownSystemUiWhileBlocked_ignore() {
        val route = decide(incoming = systemUi, kind = OverlayWindowKind.Unknown)
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_incomingCallWhileBlocked_hideNow() {
        val route = decide(
            incoming = "com.android.incallui",
            kind = OverlayWindowKind.IncomingCall,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
    }

    @Test
    fun decide_shadeWhileBlocked_ignore() {
        val route = decide(incoming = systemUi, kind = OverlayWindowKind.Shade)
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_hiddenThenSamePackage_restore() {
        val route = decide(
            incoming = blockedPkg,
            presentation = hidden,
            kind = OverlayWindowKind.BlockedApp,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Restore)
        assertEquals(blockedPkg, (route as ForegroundRoutingPolicy.Route.Restore).packageName)
    }

    @Test
    fun decide_hiddenRecentsStillTop_ignore() {
        val route = decide(
            incoming = systemUi,
            current = systemUi,
            presentation = hidden,
            kind = OverlayWindowKind.Recents,
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_launcherWithinBlockStabilization_ignore() {
        val entered = 10_000L
        val now = entered + ForegroundRoutingPolicy.BLOCK_STABILIZATION_MS - 1
        val route = decide(
            incoming = launcher,
            entered = entered,
            now = now,
            kind = OverlayWindowKind.Launcher,
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_samePackageAsCurrentWhileVisible_ignore() {
        val route = decide(incoming = blockedPkg, kind = OverlayWindowKind.BlockedApp)
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_thirdPartyWhileBlocked_hideNow() {
        val route = decide(incoming = otherApp, kind = OverlayWindowKind.OtherApp)
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
        assertEquals(otherApp, (route as ForegroundRoutingPolicy.Route.HideNow).packageName)
    }

    @Test
    fun decide_shadeWhileNotBlocking_ignore() {
        val route = decide(
            incoming = systemUi,
            blocked = null,
            blocking = false,
            presentation = BlockPresentation.None,
            kind = OverlayWindowKind.Shade,
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_homeWhileNotBlocking_commitsLeave() {
        val route = decide(
            incoming = launcher,
            current = blockedPkg,
            blocked = null,
            blocking = false,
            presentation = BlockPresentation.None,
            kind = OverlayWindowKind.Launcher,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Commit)
        assertEquals(launcher, (route as ForegroundRoutingPolicy.Route.Commit).packageName)
        assertEquals(
            ForegroundStabilizationPolicy.DEBOUNCE_MS,
            (route as ForegroundRoutingPolicy.Route.Commit).holdMs,
        )
    }

    @Test
    fun decide_recentsWhileNotBlocking_commitsLeave() {
        val route = decide(
            incoming = systemUi,
            current = blockedPkg,
            blocked = null,
            blocking = false,
            presentation = BlockPresentation.None,
            kind = OverlayWindowKind.Recents,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Commit)
    }

    @Test
    fun usageStats_staleLauncher_commitsRealApp() {
        assertEquals(
            blockedPkg,
            ForegroundRoutingPolicy.usageStatsPackageToCommit(
                usageStatsPackage = blockedPkg,
                currentForegroundPackage = launcher,
                accessibilityFocusedOnBlockedApp = false,
                blockedPackage = null,
                blockingActive = false,
                ignoredPackages = setOf(systemUi),
            ),
        )
    }

    @Test
    fun usageStats_focusedBlockedApp_ignoresOtherPackage() {
        assertEquals(
            null,
            ForegroundRoutingPolicy.usageStatsPackageToCommit(
                usageStatsPackage = otherApp,
                currentForegroundPackage = blockedPkg,
                accessibilityFocusedOnBlockedApp = true,
                blockedPackage = blockedPkg,
                blockingActive = true,
                ignoredPackages = setOf(systemUi),
            ),
        )
    }

    @Test
    fun launcherLeave_newerThanAppResume_whenAppIsNotFocused() {
        assertEquals(
            launcher,
            ForegroundRoutingPolicy.launcherLeaveFromUsage(
                latestResumePackage = launcher,
                latestResumeAtMs = 5_000L,
                currentPackage = blockedPkg,
                currentPackageLastResumeAtMs = 1_000L,
                accessibilityFocusedOnBlockedApp = false,
            ),
        )
    }

    @Test
    fun launcherLeave_ignoredWhenBlockedAppStillFocused() {
        assertEquals(
            null,
            ForegroundRoutingPolicy.launcherLeaveFromUsage(
                latestResumePackage = launcher,
                latestResumeAtMs = 5_000L,
                currentPackage = blockedPkg,
                currentPackageLastResumeAtMs = 1_000L,
                accessibilityFocusedOnBlockedApp = true,
            ),
        )
    }

    @Test
    fun launcherLeave_ignoredWhenLauncherResumeIsOlder() {
        assertEquals(
            null,
            ForegroundRoutingPolicy.launcherLeaveFromUsage(
                latestResumePackage = launcher,
                latestResumeAtMs = 500L,
                currentPackage = blockedPkg,
                currentPackageLastResumeAtMs = 1_000L,
                accessibilityFocusedOnBlockedApp = false,
            ),
        )
    }

    @Test
    fun usageStats_launcherBlip_ignored() {
        assertEquals(
            null,
            ForegroundRoutingPolicy.usageStatsPackageToCommit(
                usageStatsPackage = launcher,
                currentForegroundPackage = blockedPkg,
                accessibilityFocusedOnBlockedApp = true,
                blockedPackage = blockedPkg,
                blockingActive = true,
                ignoredPackages = setOf(systemUi),
            ),
        )
    }

    @Test
    fun confirmsExit_stillOnBlockedApp_false() {
        assertFalse(
            ForegroundRoutingPolicy.confirmsExit(blockedPkg, blockedPkg),
        )
    }

    @Test
    fun confirmsExit_launcher_true() {
        assertTrue(
            ForegroundRoutingPolicy.confirmsExit(launcher, blockedPkg),
        )
    }

    @Test
    fun confirmsExit_otherApp_true() {
        assertTrue(
            ForegroundRoutingPolicy.confirmsExit(otherApp, blockedPkg),
        )
    }

    @Test
    fun confirmsExit_nullUsageStats_false() {
        assertFalse(
            ForegroundRoutingPolicy.confirmsExit(null, blockedPkg),
        )
    }
}
