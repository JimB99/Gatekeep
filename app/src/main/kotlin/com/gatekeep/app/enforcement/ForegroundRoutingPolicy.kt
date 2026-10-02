package com.gatekeep.app.enforcement

/**
 * Routes accessibility foreground events during enforcement.
 * Transient System UI (volume, keyguard) is ignored. Recents and home hide immediately
 * while a block is showing, and still end the session when nothing is blocking.
 * Shade and unknown System UI never count as leaving.
 */
object ForegroundRoutingPolicy {

    const val SHADE_CONFIRM_HOLD_MS = 300L
    const val BLOCK_STABILIZATION_MS = 400L

    sealed interface Route {
        data object Ignore : Route
        data class Commit(val packageName: String, val holdMs: Long) : Route
        data class ConfirmExit(val packageName: String, val holdMs: Long) : Route
        data class HideNow(val packageName: String) : Route
        data class Restore(val packageName: String) : Route
    }

    fun decide(
        incomingPackage: String,
        currentForegroundPackage: String?,
        blockedPackage: String?,
        blockingActive: Boolean,
        blockEnteredAtMs: Long,
        nowMs: Long,
        presentation: BlockPresentation = BlockPresentation.None,
        kind: OverlayWindowKind,
    ): Route {
        if (!blockingActive) {
            if (currentForegroundPackage != null && incomingPackage == currentForegroundPackage) {
                return Route.Ignore
            }
            return when (kind) {
                OverlayWindowKind.TransientSystemUi,
                OverlayWindowKind.Shade,
                OverlayWindowKind.Unknown,
                -> Route.Ignore
                OverlayWindowKind.Recents,
                OverlayWindowKind.Launcher,
                -> Route.Commit(incomingPackage, ForegroundStabilizationPolicy.DEBOUNCE_MS)
                OverlayWindowKind.BlockedApp,
                OverlayWindowKind.OtherApp,
                OverlayWindowKind.IncomingCall,
                -> Route.Commit(incomingPackage, 0L)
            }
        }

        if (presentation is BlockPresentation.HiddenForOtherApp && blockedPackage != null) {
            if (kind == OverlayWindowKind.Recents ||
                kind == OverlayWindowKind.Launcher ||
                kind == OverlayWindowKind.OtherApp ||
                kind == OverlayWindowKind.IncomingCall
            ) {
                return Route.Ignore
            }
            if (kind == OverlayWindowKind.BlockedApp || incomingPackage == blockedPackage) {
                return Route.Restore(blockedPackage)
            }
            return Route.Ignore
        }

        if (currentForegroundPackage != null && incomingPackage == currentForegroundPackage) {
            return Route.Ignore
        }

        if (blockedPackage != null &&
            nowMs - blockEnteredAtMs < BLOCK_STABILIZATION_MS &&
            kind != OverlayWindowKind.OtherApp &&
            kind != OverlayWindowKind.IncomingCall &&
            kind != OverlayWindowKind.BlockedApp
        ) {
            return Route.Ignore
        }

        if (presentation is BlockPresentation.Visible) {
            return when (kind) {
                OverlayWindowKind.TransientSystemUi -> Route.Ignore
                OverlayWindowKind.Recents,
                OverlayWindowKind.Launcher,
                OverlayWindowKind.OtherApp,
                OverlayWindowKind.IncomingCall,
                -> Route.HideNow(incomingPackage)
                OverlayWindowKind.Shade,
                OverlayWindowKind.Unknown,
                -> Route.Ignore
                OverlayWindowKind.BlockedApp -> Route.Ignore
            }
        }

        return Route.Commit(incomingPackage, ForegroundStabilizationPolicy.DEBOUNCE_MS)
    }

    /** At commit time, only a real resume away from the blocked app counts as leaving. */
    fun confirmsExit(usageStatsForegroundPackage: String?, blockedPackage: String): Boolean =
        usageStatsForegroundPackage != null && usageStatsForegroundPackage != blockedPackage

    /**
     * Usage stats can see an app open while accessibility windows are still launcher or Recents.
     * A focused blocked app is not replaced by a different usage-stats package.
     */
    fun usageStatsPackageToCommit(
        usageStatsPackage: String?,
        currentForegroundPackage: String?,
        accessibilityFocusedOnBlockedApp: Boolean,
        blockedPackage: String?,
        blockingActive: Boolean,
        ignoredPackages: Set<String>,
    ): String? {
        val usage = usageStatsPackage ?: return null
        if (usage == currentForegroundPackage) return null
        if (usage in ignoredPackages || ForegroundStabilizationPolicy.isTransientForegroundPackage(usage)) {
            return null
        }
        if (blockingActive &&
            accessibilityFocusedOnBlockedApp &&
            blockedPackage != null &&
            usage != blockedPackage
        ) {
            return null
        }
        return usage
    }

    /**
     * A launcher resume newer than when the current app became foreground is a real leave
     * when accessibility no longer has that app focused. An older launcher event is
     * usage-stats lag from before this visit and must not hide the overlay.
     */
    fun launcherLeaveFromUsage(
        latestResumePackage: String?,
        latestResumeAtMs: Long,
        currentPackage: String?,
        currentPackageLastResumeAtMs: Long?,
        accessibilityFocusedOnBlockedApp: Boolean,
    ): String? {
        val latest = latestResumePackage ?: return null
        if (!ForegroundStabilizationPolicy.isTransientForegroundPackage(latest)) return null
        if (latest == currentPackage) return null
        val appResumeAt = currentPackageLastResumeAtMs ?: return null
        if (latestResumeAtMs <= appResumeAt) return null
        if (accessibilityFocusedOnBlockedApp) return null
        return latest
    }
}
