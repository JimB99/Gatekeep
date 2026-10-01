package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

object OpenGatePassPolicy {

    const val DEFAULT_GRACE_MS = SessionContinuityPolicy.RESUME_GRACE_MS

    /**
     * Skip open-gate friction only when it was already passed in the current session.
     * Session continuity (close-to-reopen within [DEFAULT_GRACE_MS]) is decided separately
     * by [SessionContinuityPolicy]; a never-passed bounce must still show on-open.
     */
    @Suppress("UNUSED_PARAMETER")
    fun shouldSkipOpenGate(
        session: SessionState?,
        nowEpochMs: Long,
        graceMs: Long = DEFAULT_GRACE_MS,
    ): Boolean {
        val passedAt = session?.openGatePassedEpochMs ?: return false
        return passedAt >= session.sessionStartEpochMs
    }

    fun markOpenGatePassed(session: SessionState, nowEpochMs: Long): SessionState =
        session.copy(openGatePassedEpochMs = nowEpochMs)
}
