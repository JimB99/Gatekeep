package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

object OpenGatePassPolicy {

    const val DEFAULT_GRACE_MS = SessionContinuityPolicy.RESUME_GRACE_MS

    /**
     * Skip on-open when it was paid in this sitting, or when wait/math/break
     * was paid ([SessionState.limitFrictionPaidThisSitting]). Session continuity
     * (leave within [DEFAULT_GRACE_MS]) is decided separately by
     * [SessionContinuityPolicy]; a never-passed bounce must still show on-open.
     */
    @Suppress("UNUSED_PARAMETER")
    fun shouldSkipOpenGate(
        session: SessionState?,
        nowEpochMs: Long,
        graceMs: Long = DEFAULT_GRACE_MS,
    ): Boolean {
        if (session == null) return false
        if (session.limitFrictionPaidThisSitting) return true
        val passedAt = session.openGatePassedEpochMs ?: return false
        return passedAt >= session.sessionStartEpochMs
    }

    fun markOpenGatePassed(session: SessionState, nowEpochMs: Long): SessionState =
        session.copy(openGatePassedEpochMs = nowEpochMs)
}
