package com.gatekeep.app.enforcement

/**
 * Overlay lifecycle when the user leaves a blocked app for home/recents without resolving the limit.
 *
 * [frictionInProgress] must not stay true after the view is removed, or [showBlocked] no-ops on return (RES-02).
 */
object LimitOverlayHidePolicy {

    /**
     * In-memory friction flag after [BlockOverlayManager.hideTemporarily].
     * Block intent ([currentRequest]) is kept elsewhere; only the active-friction guard is cleared.
     */
    fun frictionInProgressAfterTemporaryHide(): Boolean = false
}
