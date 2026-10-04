package com.gatekeep.app.enforcement

/**
 * Chooses which package's session should be marked ended when committing a new foreground app.
 * Never fall back to the newly focused package — [currentForegroundPackage] is updated before finalize.
 */
object SessionLeavePolicy {

    fun packageToFinalize(
        leavingPackage: String?,
        newlyForegroundPackage: String?,
    ): String? {
        if (leavingPackage.isNullOrEmpty()) return null
        if (leavingPackage == newlyForegroundPackage) return null
        return leavingPackage
    }
}
