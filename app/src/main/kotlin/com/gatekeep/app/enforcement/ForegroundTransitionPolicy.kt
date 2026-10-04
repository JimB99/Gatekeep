package com.gatekeep.app.enforcement

object ForegroundTransitionPolicy {

    /**
     * Returns true when a third-party foreground event should trigger enforcement.
     * Same-package events (in-app activity/window changes) are ignored.
     */
    fun shouldProcessThirdPartyForegroundChange(
        incomingPackage: String,
        currentForegroundPackage: String?,
    ): Boolean = when {
        currentForegroundPackage == null -> true
        incomingPackage != currentForegroundPackage -> true
        else -> false
    }

    /**
     * Same-package window events are usually in-app navigation. Re-evaluate when the
     * session was ended (return from background) or on-open friction has not run yet
     * (stale foreground package skipped the first open).
     */
    fun shouldReevaluateSamePackage(
        incomingPackage: String,
        currentForegroundPackage: String?,
        lastForegroundEnded: Boolean,
        openGatePassedForPackage: Boolean,
    ): Boolean {
        if (incomingPackage != currentForegroundPackage) return false
        return lastForegroundEnded || !openGatePassedForPackage
    }
}
