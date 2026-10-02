package com.gatekeep.app.enforcement

/**
 * Routes accessibility foreground events during enforcement.
 * Transient System UI (volume, etc.) is ignored; recents/home hide immediately;
 * shade/unknown system UI uses a short UsageStats confirm.
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
                OverlayWindowKind.Recents,
                OverlayWindowKind.Launcher,
                OverlayWindowKind.Unknown,
                -> Route.Ignore
                OverlayWindowKind.BlockedApp,
                OverlayWindowKind.OtherApp,
                OverlayWindowKind.IncomingCall,
                -> Route.Commit(incomingPackage, ForegroundStabilizationPolicy.DEBOUNCE_MS)
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
                -> Route.ConfirmExit(incomingPackage, SHADE_CONFIRM_HOLD_MS)
                OverlayWindowKind.BlockedApp -> Route.Ignore
            }
        }

        return Route.Commit(incomingPackage, ForegroundStabilizationPolicy.DEBOUNCE_MS)
    }

    /** At commit time, only a real resume away from the blocked app counts as leaving. */
    fun confirmsExit(usageStatsForegroundPackage: String?, blockedPackage: String): Boolean =
        usageStatsForegroundPackage != null && usageStatsForegroundPackage != blockedPackage
}
