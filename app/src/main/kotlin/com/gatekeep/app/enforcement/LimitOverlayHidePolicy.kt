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

    /**
     * [BlockOverlayManager.isVisible] must stay true until WindowManager remove finishes.
     * Clearing it when hide is only posted makes Home/PIN/settings tests see
     * coordHidden=true while UiAutomator still reads `block_message`.
     */
    fun overlayVisibleAfterHideRequested(removeCompleted: Boolean): Boolean = !removeCompleted

    /**
     * A hide that only marks the view GONE still leaves a full-screen TYPE_APPLICATION_OVERLAY
     * eating clicks (e01/e10 +5, PIN). Shrink + FLAG_NOT_TOUCHABLE until WM detach.
     */
    fun consumeTouches(hideRequested: Boolean): Boolean = !hideRequested
}
