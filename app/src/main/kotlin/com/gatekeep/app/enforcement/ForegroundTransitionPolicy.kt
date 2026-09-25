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
}
