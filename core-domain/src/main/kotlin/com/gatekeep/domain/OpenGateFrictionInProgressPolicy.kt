package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

/**
 * On-open math/wait was started but not paid. Home/recents hide must restore the same
 * challenge, not post a fresh [RuleResult.OpenDeterrent] and reset friction timing.
 *
 * [openGateBlockActive] is true when an on-open block overlay was posted for this package
 * (in-memory), so suppression works before [SessionState.frictionStartedAtEpochMs] is persisted.
 * Session friction alone does not count — limit-block math must not suppress a new open gate.
 */
object OpenGateFrictionInProgressPolicy {

    fun shouldSuppressNewOpenGateDeterrent(
        session: SessionState?,
        nowEpochMs: Long,
        openGateBlockActive: Boolean,
    ): Boolean {
        if (session != null) {
            if (OpenGatePassPolicy.shouldSkipOpenGate(session, nowEpochMs)) return false
            val pendingWait = session.pendingWaitUntilEpochMs
            if (pendingWait != null && pendingWait > nowEpochMs) return true
        }
        if (!openGateBlockActive) return false
        if (session == null) return true
        val friction = session.frictionStartedAtEpochMs ?: return true
        return friction >= session.sessionStartEpochMs
    }
}
