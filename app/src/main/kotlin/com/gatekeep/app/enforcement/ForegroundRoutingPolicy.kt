package com.gatekeep.app.enforcement

/**
 * Routes accessibility foreground events during enforcement.
 * Home/recents hide immediately; shade uses a short UsageStats confirm.
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

    fun isRecentsWindow(className: String?): Boolean {
        val name = className ?: return false
        return name.contains("recents", ignoreCase = true) ||
            name.contains("overview", ignoreCase = true)
    }

    fun isShadeWindow(className: String?): Boolean {
        val name = className ?: return false
        return name.contains("shade", ignoreCase = true) ||
            name.contains("StatusBar", ignoreCase = true) ||
            name.contains("Notification", ignoreCase = true)
    }

    fun decide(
        incomingPackage: String,
        currentForegroundPackage: String?,
        blockedPackage: String?,
        blockingActive: Boolean,
        isNoiseDestination: Boolean,
        blockEnteredAtMs: Long,
        nowMs: Long,
        presentation: BlockPresentation = BlockPresentation.None,
        windowClassName: String? = null,
    ): Route {
        if (blockingActive &&
            presentation is BlockPresentation.HiddenForOtherApp &&
            blockedPackage != null &&
            incomingPackage == blockedPackage
        ) {
            return Route.Restore(incomingPackage)
        }
        if (currentForegroundPackage != null && incomingPackage == currentForegroundPackage) {
            return Route.Ignore
        }
        if (isNoiseDestination) {
            if (!blockingActive) {
                return Route.Ignore
            }
            if (blockedPackage != null &&
                nowMs - blockEnteredAtMs < BLOCK_STABILIZATION_MS
            ) {
                return Route.Ignore
            }
            if (presentation is BlockPresentation.Visible) {
                if (isShadeWindow(windowClassName) && !isRecentsWindow(windowClassName)) {
                    return Route.ConfirmExit(incomingPackage, SHADE_CONFIRM_HOLD_MS)
                }
                return Route.HideNow(incomingPackage)
            }
            return Route.Ignore
        }
        if (blockingActive &&
            blockedPackage != null &&
            incomingPackage != blockedPackage &&
            nowMs - blockEnteredAtMs < BLOCK_STABILIZATION_MS
        ) {
            return Route.Ignore
        }
        return Route.Commit(
            incomingPackage,
            ForegroundStabilizationPolicy.DEBOUNCE_MS,
        )
    }

    /** At commit time, only a real resume away from the blocked app counts as leaving. */
    fun confirmsExit(usageStatsForegroundPackage: String?, blockedPackage: String): Boolean =
        usageStatsForegroundPackage != null && usageStatsForegroundPackage != blockedPackage
}
