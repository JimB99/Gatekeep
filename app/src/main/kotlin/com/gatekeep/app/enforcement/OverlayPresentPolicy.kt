package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason

/**
 * One evaluate-loop can post the same session-limit overlay several times a second.
 * Keep a single in-flight/visible identity until hide or the request actually changes.
 */
object OverlayPresentPolicy {

    data class Identity(
        val packageName: String,
        val reason: BlockPresentationReason,
        val isOpenGate: Boolean,
    ) {
        companion object {
            fun from(request: BlockOverlayRequest) = Identity(
                packageName = request.packageName,
                reason = request.reason,
                isOpenGate = request.isOpenGate,
            )
        }
    }

    fun shouldPost(
        incoming: Identity,
        lastPosted: Identity?,
        overlayVisible: Boolean,
        showInFlight: Boolean,
    ): Boolean {
        if (lastPosted != incoming) return true
        return shouldReshow(overlayVisible, showInFlight)
    }

    /** Restore/reshow must not tear down a visible or in-flight overlay. */
    fun shouldReshow(overlayVisible: Boolean, showInFlight: Boolean): Boolean =
        !overlayVisible && !showInFlight

    /**
     * DelayOpen posts onto the main queue. Hide / onOpenGatePassed bump the epoch
     * first; a queued show must not rebuild the wait overlay on Home or after pass.
     * A wait ticker that sees a stale epoch must remove only if it is still the
     * active wait runnable — a replaced ticker must not tear down a newer overlay.
     */
    fun shouldRunPostedShow(postedEpoch: Long, currentEpoch: Long): Boolean =
        postedEpoch == currentEpoch

    fun shouldRemoveOnStaleWaitTick(isCurrentWaitRunnable: Boolean): Boolean =
        isCurrentWaitRunnable
}
