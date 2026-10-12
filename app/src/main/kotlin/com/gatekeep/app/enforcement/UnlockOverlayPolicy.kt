package com.gatekeep.app.enforcement

/**
 * Unlock must not flash a wait overlay for [lastMonitoredPackage] while home or an
 * untracked app is actually in front. Accessibility often catches up hundreds of
 * milliseconds later (Samsung launcher after USER_PRESENT).
 */
object UnlockOverlayPolicy {

    /**
     * Package to evaluate after unlock, or null to wait for a confirmed monitored foreground.
     * [lastMonitoredPackage] is accepted only as documentation of the stale hint — it must
     * not override a known leave surface or untracked app.
     */
    fun resumeTarget(
        currentForegroundPackage: String?,
        lastMonitoredPackage: String?,
        usageFallbackPackage: String?,
        topWindowPackage: String?,
        topWindowKind: OverlayWindowKind?,
        monitoredPackages: Set<String>,
        ignoredPackages: Set<String>,
        overlayOwnerPackage: String,
    ): String? {
        if (topWindowPackage != null) {
            confirmedMonitoredOrNull(
                pkg = topWindowPackage,
                kind = topWindowKind,
                monitoredPackages = monitoredPackages,
                ignoredPackages = ignoredPackages,
                overlayOwnerPackage = overlayOwnerPackage,
            )?.let { return it }
            if (isLeaveOrUntracked(
                    pkg = topWindowPackage,
                    kind = topWindowKind,
                    monitoredPackages = monitoredPackages,
                    overlayOwnerPackage = overlayOwnerPackage,
                )
            ) {
                return null
            }
        }
        currentForegroundPackage?.let { current ->
            confirmedMonitoredOrNull(
                pkg = current,
                kind = null,
                monitoredPackages = monitoredPackages,
                ignoredPackages = ignoredPackages,
                overlayOwnerPackage = overlayOwnerPackage,
            )?.let { return it }
            if (isLeaveOrUntracked(
                    pkg = current,
                    kind = null,
                    monitoredPackages = monitoredPackages,
                    overlayOwnerPackage = overlayOwnerPackage,
                )
            ) {
                return null
            }
        }
        usageFallbackPackage?.let { usage ->
            confirmedMonitoredOrNull(
                pkg = usage,
                kind = null,
                monitoredPackages = monitoredPackages,
                ignoredPackages = ignoredPackages,
                overlayOwnerPackage = overlayOwnerPackage,
            )?.let { return it }
            if (isLeaveOrUntracked(
                    pkg = usage,
                    kind = null,
                    monitoredPackages = monitoredPackages,
                    overlayOwnerPackage = overlayOwnerPackage,
                )
            ) {
                return null
            }
        }
        return lastMonitoredPackage?.let { last ->
            confirmedMonitoredOrNull(
                pkg = last,
                kind = null,
                monitoredPackages = monitoredPackages,
                ignoredPackages = ignoredPackages,
                overlayOwnerPackage = overlayOwnerPackage,
            )
        }
    }

    /**
     * Stale UsageStats still naming the blocked app is not enough to restore while
     * windows are unknown or still launcher/recents.
     */
    fun shouldRestoreHiddenBlockFromPoll(
        usagePackage: String?,
        blockedPackage: String?,
        topWindowPackage: String?,
        topWindowKind: OverlayWindowKind?,
    ): Boolean {
        if (blockedPackage == null || usagePackage != blockedPackage) return false
        if (topWindowPackage == null || topWindowKind == null) return false
        if (topWindowKind == OverlayWindowKind.Launcher ||
            topWindowKind == OverlayWindowKind.Recents
        ) {
            return false
        }
        return topWindowPackage == blockedPackage
    }

    private fun confirmedMonitoredOrNull(
        pkg: String,
        kind: OverlayWindowKind?,
        monitoredPackages: Set<String>,
        ignoredPackages: Set<String>,
        overlayOwnerPackage: String,
    ): String? {
        if (isLeaveOrUntracked(pkg, kind, monitoredPackages, overlayOwnerPackage)) return null
        if (pkg in ignoredPackages) return null
        if (pkg !in monitoredPackages) return null
        return pkg
    }

    /**
     * Home, Recents, or a third-party app that is not monitored. System UI / unknown
     * is not a leave — unlock may still be returning to the last monitored app (RES-01).
     */
    fun isLeaveOrUntracked(
        pkg: String,
        kind: OverlayWindowKind?,
        monitoredPackages: Set<String>,
        overlayOwnerPackage: String,
    ): Boolean {
        if (ForegroundStabilizationPolicy.isTransientForegroundPackage(pkg)) return true
        val resolvedKind = kind ?: OverlayWindowClassifier.classify(pkg, null, null)
        if (resolvedKind == OverlayWindowKind.Launcher ||
            resolvedKind == OverlayWindowKind.Recents ||
            resolvedKind == OverlayWindowKind.IncomingCall
        ) {
            return true
        }
        if (resolvedKind == OverlayWindowKind.OtherApp && pkg !in monitoredPackages) {
            return true
        }
        if (pkg == overlayOwnerPackage && pkg !in monitoredPackages) return true
        return false
    }
}
