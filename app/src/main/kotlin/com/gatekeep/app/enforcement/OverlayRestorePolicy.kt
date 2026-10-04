package com.gatekeep.app.enforcement

/**
 * Combines window z-order, focus, UsageStats, and presentation so Recents/home
 * hide immediately but cannot skip restore, and in-app surfaces do not look like a leave.
 */
object OverlayRestorePolicy {

    data class Input(
        val windows: List<OverlayWindowSnapshot>,
        val usageStatsForegroundPackage: String?,
        val currentForegroundPackage: String?,
        val blockedPackage: String?,
        val blockingActive: Boolean,
        val presentation: BlockPresentation,
        val overlayOwnerPackage: String,
        val lastMonitoredPackage: String? = null,
        val lastMonitoredAtMs: Long = 0L,
        val nowMs: Long = 0L,
        val blockEnteredAtMs: Long = 0L,
        val lastRestoreAtMs: Long = 0L,
        val reportedForegroundPackage: String? = null,
        val ignoredPackages: Set<String> = emptySet(),
    )

    fun decide(input: Input): ForegroundRoutingPolicy.Route {
        val top = OverlayWindowClassifier.pickTopRelevantWindow(
            windows = input.windows,
            blockedPackage = input.blockedPackage,
            overlayOwnerPackage = input.overlayOwnerPackage,
        )
        if (top == null) {
            val blocked = input.blockedPackage
            if (input.presentation is BlockPresentation.HiddenForOtherApp &&
                blocked != null &&
                input.usageStatsForegroundPackage == blocked
            ) {
                return ForegroundRoutingPolicy.Route.Restore(blocked)
            }
            val reported = input.reportedForegroundPackage
            if (!input.blockingActive && reported != null && isAppPackage(reported, input)) {
                return ForegroundRoutingPolicy.Route.Commit(
                    reported,
                    ForegroundStabilizationPolicy.DEBOUNCE_MS,
                )
            }
            return ForegroundRoutingPolicy.Route.Ignore
        }

        val kind = OverlayWindowClassifier.classify(
            top.packageName,
            top.className,
            input.blockedPackage,
        )

        val reported = input.reportedForegroundPackage
        if (!input.blockingActive &&
            reported != null &&
            isAppPackage(reported, input)
        ) {
            return ForegroundRoutingPolicy.Route.Commit(
                reported,
                ForegroundStabilizationPolicy.DEBOUNCE_MS,
            )
        }

        if (input.blockingActive &&
            input.presentation is BlockPresentation.Visible &&
            reported != null
        ) {
            val reportedKind = OverlayWindowClassifier.classify(reported, null, input.blockedPackage)
            if (reportedKind == OverlayWindowKind.Launcher || reportedKind == OverlayWindowKind.Recents) {
                return ForegroundRoutingPolicy.Route.HideNow(reported)
            }
        }

        if (input.blockingActive &&
            input.presentation is BlockPresentation.Visible &&
            isEmbeddedBrowser(top.className) &&
            input.windows.any { !it.isOverlay && it.packageName == input.blockedPackage }
        ) {
            return ForegroundRoutingPolicy.Route.Ignore
        }

        if (input.blockingActive &&
            ForegroundStabilizationPolicy.shouldApplyMonitoredGrace(
                incomingPackage = top.packageName,
                ignoredPackages = input.ignoredPackages,
                lastMonitoredPackage = input.lastMonitoredPackage,
                lastMonitoredAtMs = input.lastMonitoredAtMs,
                nowMs = input.nowMs,
            ) &&
            kind != OverlayWindowKind.Recents &&
            kind != OverlayWindowKind.Launcher &&
            kind != OverlayWindowKind.OtherApp &&
            kind != OverlayWindowKind.IncomingCall
        ) {
            return ForegroundRoutingPolicy.Route.Ignore
        }

        val blocked = input.blockedPackage
        if (input.presentation is BlockPresentation.HiddenForOtherApp && blocked != null) {
            if (input.reportedForegroundPackage == blocked &&
                (kind == OverlayWindowKind.Recents || kind == OverlayWindowKind.Launcher)
            ) {
                return ForegroundRoutingPolicy.Route.Ignore
            }
            if (kind == OverlayWindowKind.Recents || kind == OverlayWindowKind.Launcher) {
                val blockedWindow = input.windows.firstOrNull {
                    !it.isOverlay && it.packageName == blocked
                }
                if (blockedWindow != null && blockedWindow.isFocused) {
                    return ForegroundRoutingPolicy.Route.Restore(blocked)
                }
                if (blockedWindow != null && blockedWindow.isActive && !top.isFocused) {
                    return ForegroundRoutingPolicy.Route.Restore(blocked)
                }
                return ForegroundRoutingPolicy.Route.Ignore
            }
        }

        if (input.blockingActive &&
            input.presentation is BlockPresentation.Visible &&
            blocked != null &&
            (kind == OverlayWindowKind.Recents || kind == OverlayWindowKind.Launcher)
        ) {
            if (input.reportedForegroundPackage == blocked) {
                return ForegroundRoutingPolicy.Route.Ignore
            }
            if (input.lastRestoreAtMs > 0L &&
                input.nowMs - input.lastRestoreAtMs < ForegroundRoutingPolicy.BLOCK_STABILIZATION_MS
            ) {
                return ForegroundRoutingPolicy.Route.Ignore
            }
        }

        return ForegroundRoutingPolicy.decide(
            incomingPackage = top.packageName,
            currentForegroundPackage = input.currentForegroundPackage,
            blockedPackage = input.blockedPackage,
            blockingActive = input.blockingActive,
            blockEnteredAtMs = input.blockEnteredAtMs,
            nowMs = input.nowMs,
            presentation = input.presentation,
            kind = kind,
        )
    }

    private fun isEmbeddedBrowser(className: String?): Boolean {
        val name = className.orEmpty().lowercase()
        return name.contains("customtab")
    }

    private fun isAppPackage(packageName: String, input: Input): Boolean {
        val kind = OverlayWindowClassifier.classify(packageName, null, input.blockedPackage)
        return kind == OverlayWindowKind.BlockedApp || kind == OverlayWindowKind.OtherApp
    }
}
