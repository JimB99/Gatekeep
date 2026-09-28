package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

object OpenGatePassPolicy {

    const val DEFAULT_GRACE_MS = 60_000L

    /**
     * Skip open-gate friction when the user already passed for this session or within [graceMs].
     */
    fun shouldSkipOpenGate(
        session: SessionState?,
        nowEpochMs: Long,
        graceMs: Long = DEFAULT_GRACE_MS,
    ): Boolean {
        val passedAt = session?.openGatePassedEpochMs ?: return false
        if (nowEpochMs - passedAt < graceMs) return true
        return passedAt >= session.sessionStartEpochMs
    }

    fun markOpenGatePassed(session: SessionState, nowEpochMs: Long): SessionState =
        session.copy(openGatePassedEpochMs = nowEpochMs)
}
