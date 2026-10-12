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
}
