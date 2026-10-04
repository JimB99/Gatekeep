package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRestorePolicyTest {

    private val blocked = "com.instagram.android"
    private val owner = "com.gatekeep.app"
    private val systemUi = "com.android.systemui"
    private val launcher = "com.android.launcher3"
    private val chrome = "com.android.chrome"
    private val visible = BlockPresentation.Visible(blocked, 1L)
    private val hidden = BlockPresentation.HiddenForOtherApp(blocked, 1L)

    private fun recents(focused: Boolean = true) = OverlayWindowSnapshot(
        packageName = systemUi,
        className = "com.android.quickstep.RecentsActivity",
        isOverlay = false,
        isFocused = focused,
        isActive = focused,
    )

    private fun app(focused: Boolean = false) = OverlayWindowSnapshot(
        packageName = blocked,
        className = "com.instagram.android.activity.MainTabActivity",
        isOverlay = false,
        isFocused = focused,
        isActive = focused,
    )

    private fun zoom() = OverlayWindowSnapshot(
        packageName = blocked,
        className = "com.instagram.feed.overview.MediaOverviewActivity",
        isOverlay = false,
        isFocused = true,
        isActive = true,
    )

    private fun input(
        windows: List<OverlayWindowSnapshot>,
        usage: String? = blocked,
        current: String? = blocked,
        presentation: BlockPresentation = visible,
        blocking: Boolean = true,
        lastMonitored: String? = blocked,
        lastMonitoredAt: Long = 9_000L,
        now: Long = 10_000L,
        entered: Long = 0L,
        lastRestore: Long = 0L,
        reported: String? = null,
    ) = OverlayRestorePolicy.Input(
        windows = windows,
        usageStatsForegroundPackage = usage,
        currentForegroundPackage = current,
        blockedPackage = blocked,
        blockingActive = blocking,
        presentation = presentation,
        overlayOwnerPackage = owner,
        lastMonitoredPackage = lastMonitored,
        lastMonitoredAtMs = lastMonitoredAt,
        nowMs = now,
        blockEnteredAtMs = entered,
        lastRestoreAtMs = lastRestore,
        reportedForegroundPackage = reported,
        ignoredPackages = setOf(systemUi),
    )

    @Test
    fun visibleRecentsFocused_hideNow() {
        val route = OverlayRestorePolicy.decide(
            input(listOf(recents(focused = true), app()), usage = systemUi, current = blocked),
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
    }

    @Test
    fun hiddenStaleRecentsUnfocused_blockedFocused_restore() {
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents(focused = false), app(focused = true)),
                usage = blocked,
                current = systemUi,
                presentation = hidden,
            ),
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Restore)
        assertEquals(blocked, (route as ForegroundRoutingPolicy.Route.Restore).packageName)
    }

    @Test
    fun hiddenRecentsTop_usageStatsBlocked_staysHidden() {
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents(focused = true), app()),
                usage = blocked,
                current = systemUi,
                presentation = hidden,
            ),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun hiddenRecentsTop_reportedForegroundBlocked_staysHidden() {
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents(focused = true)),
                usage = systemUi,
                current = systemUi,
                presentation = hidden,
                reported = blocked,
            ),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun visibleRecents_justRestored_ignore() {
        val now = 10_000L
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents(focused = true)),
                usage = systemUi,
                current = blocked,
                presentation = visible,
                now = now,
                lastRestore = now - 50L,
            ),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun visibleRecents_reportedForegroundBlocked_ignore() {
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents(focused = true), app()),
                usage = systemUi,
                current = blocked,
                presentation = visible,
                reported = blocked,
            ),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun hiddenRecentsTop_usageStatsStillRecents_ignore() {
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents(focused = true)),
                usage = systemUi,
                current = systemUi,
                presentation = hidden,
            ),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun visibleInstagramZoomOverviewClass_ignore() {
        val route = OverlayRestorePolicy.decide(
            input(windows = listOf(zoom()), current = blocked),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun visibleVolume_ignore() {
        val volume = OverlayWindowSnapshot(
            packageName = systemUi,
            className = "com.android.systemui.volume.VolumeDialog",
            isOverlay = false,
        )
        val route = OverlayRestorePolicy.decide(
            input(windows = listOf(volume, app(focused = true))),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun visibleChromeCustomTabWhileBlockedStillInStack_ignore() {
        val chromeWindow = OverlayWindowSnapshot(
            packageName = chrome,
            className = "org.chromium.chrome.browser.customtabs.CustomTabActivity",
            isOverlay = false,
            isFocused = true,
            isActive = true,
        )
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(chromeWindow, app()),
                usage = chrome,
                lastMonitoredAt = 10_000L - 500L,
                now = 10_000L,
                entered = 0L,
            ),
        )
        assertEquals(ForegroundRoutingPolicy.Route.Ignore, route)
    }

    @Test
    fun visibleOtherAppAfterGrace_hideNow() {
        val other = OverlayWindowSnapshot(
            packageName = "com.example.other",
            className = "com.example.other.MainActivity",
            isOverlay = false,
            isFocused = true,
            isActive = true,
        )
        val now = 10_000L
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(other),
                usage = "com.example.other",
                lastMonitoredAt = now - ForegroundStabilizationPolicy.MONITORED_GRACE_MS - 1,
                now = now,
                entered = now - 5_000L,
            ),
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.HideNow)
    }

    @Test
    fun hiddenHomeThenBlockedUsage_restore() {
        val home = OverlayWindowSnapshot(
            packageName = launcher,
            className = "com.android.launcher3.Launcher",
            isOverlay = false,
            isFocused = false,
            isActive = false,
        )
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(home, app(focused = true)),
                usage = blocked,
                current = launcher,
                presentation = hidden,
            ),
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Restore)
    }

    @Test
    fun notBlocking_recents_reportedApp_commitsReported() {
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents()),
                usage = systemUi,
                blocking = false,
                presentation = BlockPresentation.None,
                lastMonitored = null,
                reported = blocked,
            ),
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Commit)
        assertEquals(blocked, (route as ForegroundRoutingPolicy.Route.Commit).packageName)
    }

    @Test
    fun notBlocking_recents_commitsLeave() {
        val route = OverlayRestorePolicy.decide(
            input(
                windows = listOf(recents()),
                usage = systemUi,
                blocking = false,
                presentation = BlockPresentation.None,
                lastMonitored = null,
            ),
        )
        assertTrue(route is ForegroundRoutingPolicy.Route.Commit)
        assertEquals(systemUi, (route as ForegroundRoutingPolicy.Route.Commit).packageName)
    }
}
