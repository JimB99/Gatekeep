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

    @Test
    fun decide_homeWhileBlocked_hideNow() {
        val now = 10_000L
        val entered = now - ForegroundRoutingPolicy.BLOCK_STABILIZATION_MS
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = launcher,
            currentForegroundPackage = blockedPkg,
            blockedPackage = blockedPkg,
            blockingActive = true,
            isNoiseDestination = true,
            blockEnteredAtMs = entered,
            nowMs = now,
            presentation = visible,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
        assertEquals(launcher, (route as ForegroundRoutingPolicy.Route.HideNow).packageName)
    }

    @Test
    fun decide_overviewWhileBlocked_hideNow() {
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = systemUi,
            currentForegroundPackage = blockedPkg,
            blockedPackage = blockedPkg,
            blockingActive = true,
            isNoiseDestination = true,
            blockEnteredAtMs = 0L,
            nowMs = 10_000L,
            presentation = visible,
            windowClassName = "com.android.quickstep.RecentsActivity",
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
    }

    @Test
    fun decide_shadeWhileBlocked_confirmExit() {
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = systemUi,
            currentForegroundPackage = blockedPkg,
            blockedPackage = blockedPkg,
            blockingActive = true,
            isNoiseDestination = true,
            blockEnteredAtMs = 0L,
            nowMs = 10_000L,
            presentation = visible,
            windowClassName = "com.android.systemui.statusbar.NotificationShadeWindowView",
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.ConfirmExit)
        val confirm = route as ForegroundRoutingPolicy.Route.ConfirmExit
        assertEquals(ForegroundRoutingPolicy.SHADE_CONFIRM_HOLD_MS, confirm.holdMs)
    }

    @Test
    fun decide_hiddenThenSamePackage_restore() {
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = blockedPkg,
            currentForegroundPackage = blockedPkg,
            blockedPackage = blockedPkg,
            blockingActive = true,
            isNoiseDestination = false,
            blockEnteredAtMs = 0L,
            nowMs = 10_000L,
            presentation = hidden,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Restore)
        assertEquals(blockedPkg, (route as ForegroundRoutingPolicy.Route.Restore).packageName)
    }

    @Test
    fun decide_launcherWithinBlockStabilization_ignore() {
        val entered = 10_000L
        val now = entered + ForegroundRoutingPolicy.BLOCK_STABILIZATION_MS - 1
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = launcher,
            currentForegroundPackage = blockedPkg,
            blockedPackage = blockedPkg,
            blockingActive = true,
            isNoiseDestination = true,
            blockEnteredAtMs = entered,
            nowMs = now,
            presentation = visible,
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_samePackageAsCurrentWhileVisible_ignore() {
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = blockedPkg,
            currentForegroundPackage = blockedPkg,
            blockedPackage = blockedPkg,
            blockingActive = true,
            isNoiseDestination = false,
            blockEnteredAtMs = 0L,
            nowMs = 10_000L,
            presentation = visible,
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun decide_thirdPartyWhileBlocked_commitWithStandardDebounce() {
        val now = 10_000L
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = otherApp,
            currentForegroundPackage = blockedPkg,
            blockedPackage = blockedPkg,
            blockingActive = true,
            isNoiseDestination = false,
            blockEnteredAtMs = 0L,
            nowMs = now,
            presentation = visible,
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Commit)
        val commit = route as ForegroundRoutingPolicy.Route.Commit
        assertEquals(otherApp, commit.packageName)
        assertEquals(ForegroundStabilizationPolicy.DEBOUNCE_MS, commit.holdMs)
    }

    @Test
    fun decide_noiseWhileNotBlocking_ignore() {
        val route = ForegroundRoutingPolicy.decide(
            incomingPackage = launcher,
            currentForegroundPackage = blockedPkg,
            blockedPackage = null,
            blockingActive = false,
            isNoiseDestination = true,
            blockEnteredAtMs = 0L,
            nowMs = 10_000L,
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
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
