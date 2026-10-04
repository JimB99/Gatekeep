package com.gatekeep.app.enforcement

/**
 * Same-package accessibility events are usually in-app navigation. Re-evaluate when
 * open-gate or session-resume needs a pass, but not while the block overlay was hidden
 * because the user left for home/recents.
 */
object SamePackageReevaluationPolicy {

    fun shouldReevaluateSamePackageCommit(
        packageName: String,
        currentForegroundPackage: String?,
        blockedPackage: String?,
        blockingActive: Boolean,
        presentation: BlockPresentation,
        sessionStartedForPackage: String?,
        openGatePassedPackage: String?,
    ): Boolean {
        if (blockingActive &&
            blockedPackage == packageName &&
            presentation is BlockPresentation.Visible
        ) {
            return false
        }
        if (blockingActive &&
            blockedPackage == packageName &&
            presentation is BlockPresentation.HiddenForOtherApp
        ) {
            return false
        }
        return ForegroundTransitionPolicy.shouldReevaluateSamePackage(
            incomingPackage = packageName,
            currentForegroundPackage = currentForegroundPackage,
            lastForegroundEnded = sessionStartedForPackage != packageName,
            openGatePassedForPackage = openGatePassedPackage == packageName,
        )
    }
}
