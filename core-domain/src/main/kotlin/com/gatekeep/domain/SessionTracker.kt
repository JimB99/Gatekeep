package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

object SessionTracker {

    fun sessionDurationMs(session: SessionState?, nowEpochMs: Long): Long {
        if (session == null) return 0
        var duration = (nowEpochMs - session.sessionStartEpochMs).coerceAtLeast(0)
        duration -= session.excludedMs
        session.frictionStartedAtEpochMs?.let { started ->
            duration -= (nowEpochMs - started).coerceAtLeast(0)
        }
        return duration.coerceAtLeast(0)
    }

    fun isOnBreak(session: SessionState?, nowEpochMs: Long): Boolean {
        val breakUntil = session?.breakUntilEpochMs ?: return false
        return nowEpochMs < breakUntil
    }

    fun breakRemainingMs(session: SessionState?, nowEpochMs: Long): Long {
        if (!isOnBreak(session, nowEpochMs)) return 0
        return (session!!.breakUntilEpochMs!! - nowEpochMs).coerceAtLeast(0)
    }

    fun evaluateSession(
        limit: com.gatekeep.domain.model.AppLimit?,
        session: SessionState?,
        nowEpochMs: Long,
    ): SessionCheckResult {
        if (limit == null || !limit.enabled) {
            return SessionCheckResult.Allowed(remainingSessionMs = null)
        }

        if (isOnBreak(session, nowEpochMs)) {
            return SessionCheckResult.OnBreak(
                breakUntilEpochMs = session!!.breakUntilEpochMs!!,
                remainingMs = breakRemainingMs(session, nowEpochMs),
            )
        }

        val sessionLimit = limit.sessionLimitMs
        if (sessionLimit == null) {
            return SessionCheckResult.Allowed(remainingSessionMs = null)
        }

        val duration = sessionDurationMs(session, nowEpochMs)
        val remaining = sessionLimit - duration

        return if (remaining <= 0) {
            val breakDuration = limit.breakDurationMs ?: 0L
            val breakUntil = if (breakDuration > 0) nowEpochMs + breakDuration else null
            SessionCheckResult.SessionExceeded(breakUntilEpochMs = breakUntil)
        } else {
            SessionCheckResult.Allowed(remainingSessionMs = remaining)
        }
    }

    fun startSession(packageName: String, nowEpochMs: Long): SessionState =
        SessionState(packageName = packageName, sessionStartEpochMs = nowEpochMs)

    fun applyBreak(session: SessionState, breakUntilEpochMs: Long): SessionState =
        session.copy(breakUntilEpochMs = breakUntilEpochMs)

    fun clearBreak(session: SessionState): SessionState =
        session.copy(breakUntilEpochMs = null)

    fun startFriction(session: SessionState, nowEpochMs: Long): SessionState =
        session.copy(frictionStartedAtEpochMs = nowEpochMs)

    fun endFriction(session: SessionState, nowEpochMs: Long): SessionState {
        val started = session.frictionStartedAtEpochMs ?: return session
        val added = (nowEpochMs - started).coerceAtLeast(0)
        return session.copy(
            excludedMs = session.excludedMs + added,
            frictionStartedAtEpochMs = null,
        )
    }

    fun addExcludedTime(session: SessionState, ms: Long): SessionState =
        session.copy(excludedMs = session.excludedMs + ms.coerceAtLeast(0))

    sealed class SessionCheckResult {
        data class Allowed(val remainingSessionMs: Long?) : SessionCheckResult()
        data class OnBreak(val breakUntilEpochMs: Long, val remainingMs: Long) : SessionCheckResult()
        data class SessionExceeded(val breakUntilEpochMs: Long?) : SessionCheckResult()
    }
}
