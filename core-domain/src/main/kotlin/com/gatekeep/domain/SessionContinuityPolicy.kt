package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

object SessionContinuityPolicy {

    const val RESUME_GRACE_MS = 60_000L

    fun shouldContinueSession(
        lastForegroundEndEpochMs: Long?,
        nowEpochMs: Long,
        graceMs: Long = RESUME_GRACE_MS,
    ): Boolean {
        if (lastForegroundEndEpochMs == null) return false
        return nowEpochMs - lastForegroundEndEpochMs < graceMs
    }

    fun markForegroundEnded(session: SessionState, nowEpochMs: Long): SessionState =
        session.copy(lastForegroundEndEpochMs = nowEpochMs)

    fun resumeAfterGap(session: SessionState, nowEpochMs: Long): SessionState {
        val endedAt = session.lastForegroundEndEpochMs ?: return session.copy(
            lastForegroundEndEpochMs = null,
        )
        val gapMs = (nowEpochMs - endedAt).coerceAtLeast(0)
        return session.copy(
            excludedMs = session.excludedMs + gapMs,
            lastForegroundEndEpochMs = null,
        )
    }
}
