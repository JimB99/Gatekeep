package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

object SessionTracker {

    fun sessionDurationMs(session: SessionState?, nowEpochMs: Long): Long {
        if (session == null) return 0
        val clockMs = sessionClockMs(session, nowEpochMs)
        var duration = (clockMs - session.sessionStartEpochMs).coerceAtLeast(0)
        duration -= session.excludedMs
        session.frictionStartedAtEpochMs?.let { started ->
            duration -= (clockMs - started).coerceAtLeast(0)
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

    fun breakUntilFromCrossed(limitCrossedAtMs: Long, breakDurationMs: Long?): Long? {
        if (breakDurationMs == null || breakDurationMs <= 0) return null
        return limitCrossedAtMs + breakDurationMs
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

        val clockMs = sessionClockMs(session, nowEpochMs)
        val elapsed = session?.let { (clockMs - it.sessionStartEpochMs).coerceAtLeast(0) } ?: 0L
        val activeFrictionMs = session?.frictionStartedAtEpochMs?.let { started ->
            (clockMs - started).coerceAtLeast(0)
        } ?: 0L
        val excludedMs = session?.excludedMs ?: 0L
        val netConsumedMs = (elapsed - excludedMs).coerceAtLeast(0)

        // RES-02: once wall-clock usage (minus exclusions) reached the cap, friction pause must not re-allow.
        if (netConsumedMs >= sessionLimit) {
            val limitCrossedAt = session?.let {
                it.sessionStartEpochMs + sessionLimit - excludedMs + activeFrictionMs
            } ?: nowEpochMs
            val breakUntil = breakUntilFromCrossed(limitCrossedAt, limit.breakDurationMs)
            return SessionCheckResult.SessionExceeded(
                breakUntilEpochMs = breakUntil,
                limitCrossedAtEpochMs = limitCrossedAt,
            )
        }

        val remaining = sessionLimit - elapsed + excludedMs + activeFrictionMs

        return if (remaining <= 0) {
            val limitCrossedAt = session?.let {
                it.sessionStartEpochMs + sessionLimit - excludedMs + activeFrictionMs
            } ?: nowEpochMs
            val breakUntil = breakUntilFromCrossed(limitCrossedAt, limit.breakDurationMs)
            SessionCheckResult.SessionExceeded(
                breakUntilEpochMs = breakUntil,
                limitCrossedAtEpochMs = limitCrossedAt,
            )
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

    /**
     * When a mandatory break has ended, start a fresh session so the user can continue
     * without leaving the app. Returns [session] unchanged if still on break or no break set.
     */
    fun completeExpiredBreak(session: SessionState, nowEpochMs: Long): SessionState {
        val breakUntil = session.breakUntilEpochMs ?: return session
        if (nowEpochMs < breakUntil) return session
        return startSession(session.packageName, nowEpochMs)
    }

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

    /**
     * Freeze the session clock at [nowEpochMs] while the device is locked / screen-off (RES-04).
     * Idempotent: a second pause keeps the original timestamp so pocket time cannot resume the clock.
     */
    fun pauseDeviceUse(session: SessionState, nowEpochMs: Long): SessionState {
        if (session.devicePausedAtEpochMs != null) return session
        return session.copy(devicePausedAtEpochMs = nowEpochMs)
    }

    /**
     * Unpause: add locked time to [SessionState.excludedMs] and extend a pending wait (RES-04).
     */
    fun resumeDeviceUse(session: SessionState, nowEpochMs: Long): SessionState {
        val pausedAt = session.devicePausedAtEpochMs ?: return session
        val pausedMs = (nowEpochMs - pausedAt).coerceAtLeast(0)
        return extendPendingWait(
            session.copy(
                excludedMs = session.excludedMs + pausedMs,
                devicePausedAtEpochMs = null,
            ),
            pausedMs,
        )
    }

    fun hasPendingWait(session: SessionState?, nowEpochMs: Long): Boolean {
        val until = session?.pendingWaitUntilEpochMs ?: return false
        return nowEpochMs < until
    }

    fun pendingWaitRemainingMs(session: SessionState?, nowEpochMs: Long): Long {
        if (!hasPendingWait(session, nowEpochMs)) return 0
        return (session!!.pendingWaitUntilEpochMs!! - nowEpochMs).coerceAtLeast(0)
    }

    fun extendPendingWait(session: SessionState, extraMs: Long): SessionState {
        val until = session.pendingWaitUntilEpochMs ?: return session
        if (extraMs <= 0) return session
        return session.copy(pendingWaitUntilEpochMs = until + extraMs)
    }

    fun setPendingWait(session: SessionState, untilEpochMs: Long): SessionState =
        session.copy(pendingWaitUntilEpochMs = untilEpochMs)

    fun clearPendingWait(session: SessionState): SessionState =
        session.copy(pendingWaitUntilEpochMs = null)

    fun markSessionLimitNotified(session: SessionState): SessionState =
        session.copy(sessionLimitNotified = true)

    fun incrementConsecutiveExtensions(session: SessionState): SessionState =
        session.copy(consecutiveExtensionCount = session.consecutiveExtensionCount + 1)

    fun resetConsecutiveExtensions(session: SessionState): SessionState =
        session.copy(consecutiveExtensionCount = 0)

    private fun sessionClockMs(session: SessionState?, nowEpochMs: Long): Long {
        val freezeAt = listOfNotNull(
            session?.devicePausedAtEpochMs,
            session?.lastForegroundEndEpochMs,
        ).minOrNull() ?: return nowEpochMs
        return minOf(freezeAt, nowEpochMs)
    }

    sealed class SessionCheckResult {
        data class Allowed(val remainingSessionMs: Long?) : SessionCheckResult()
        data class OnBreak(val breakUntilEpochMs: Long, val remainingMs: Long) : SessionCheckResult()
        data class SessionExceeded(
            val breakUntilEpochMs: Long?,
            val limitCrossedAtEpochMs: Long = 0L,
        ) : SessionCheckResult()
    }
}
