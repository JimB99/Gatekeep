package com.gatekeep.app.enforcement

import com.gatekeep.domain.OpenGateFrictionInProgressPolicy
import com.gatekeep.domain.model.BlockPresentationReason
import com.gatekeep.domain.model.SessionState

/**
 * Overlay restore + re-eval must reshown stored on-open friction, not post a new deterrent.
 */
object OpenGateDeterrentSuppressPolicy {

    fun shouldReshowInsteadOfNewDeterrent(
        packageName: String,
        session: SessionState?,
        nowEpochMs: Long,
        lastPosted: OverlayPresentPolicy.Identity?,
        isBlockingActive: Boolean,
        blockedPackage: String?,
    ): Boolean {
        val openGatePosted = isBlockingActive &&
            blockedPackage == packageName &&
            lastPosted != null &&
            lastPosted.packageName == packageName &&
            lastPosted.isOpenGate &&
            lastPosted.reason == BlockPresentationReason.openGate
        return OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
            session = session,
            nowEpochMs = nowEpochMs,
            openGateBlockActive = openGatePosted,
        )
    }
}
