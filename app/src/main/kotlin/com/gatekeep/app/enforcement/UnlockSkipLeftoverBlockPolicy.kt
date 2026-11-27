package com.gatekeep.app.enforcement

/**
 * Unlock on home/Recents must drop a leftover block for the last monitored app
 * so a later UsageStats poll cannot overlay_restore it (RES-08).
 */
object UnlockSkipLeftoverBlockPolicy {

    fun shouldClearLeftoverBlock(
        presentation: BlockPresentation,
        lastMonitoredPackage: String?,
        currentForegroundPackage: String?,
        topWindowKind: OverlayWindowKind?,
    ): Boolean {
        val blocked = when (presentation) {
            is BlockPresentation.Visible -> presentation.packageName
            is BlockPresentation.HiddenForOtherApp -> presentation.packageName
            BlockPresentation.None -> return false
        }
        if (lastMonitoredPackage != blocked) return false
        if (topWindowKind == OverlayWindowKind.Launcher ||
            topWindowKind == OverlayWindowKind.Recents
        ) {
            return true
        }
        val current = currentForegroundPackage ?: return false
        return ForegroundRoutingPolicy.isCommittedLeaveSurface(current)
    }
}
