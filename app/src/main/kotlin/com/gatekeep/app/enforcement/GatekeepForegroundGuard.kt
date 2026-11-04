package com.gatekeep.app.enforcement

/**
 * While Gatekeep settings UI is the committed foreground, stale accessibility / usage
 * events for the last blocked app must not replace it or re-show the block overlay.
 */
object GatekeepForegroundGuard {

    fun shouldSuppressThirdPartyForegroundRoute(
        gatekeepPackage: String,
        currentForegroundPackage: String?,
        incomingPackage: String,
    ): Boolean {
        if (currentForegroundPackage != gatekeepPackage) return false
        if (incomingPackage == gatekeepPackage) return false
        if (ForegroundRoutingPolicy.isCommittedLeaveSurface(incomingPackage)) return false
        return true
    }
}
