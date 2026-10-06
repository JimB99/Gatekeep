package com.gatekeep.app.enforcement

/**
 * Session HUD remaining must not tick on wall clock while the monitored app is away
 * ([lastForegroundEndEpochMs]) or the device is locked ([devicePausedAtEpochMs], RES-04).
 */
object SessionHudDeadline {

    fun sessionDeadlineEpochMs(
        nowEpochMs: Long,
        remainingSessionMs: Long?,
        lastForegroundEndEpochMs: Long?,
        devicePausedAtEpochMs: Long? = null,
    ): Long? {
        if (remainingSessionMs == null || remainingSessionMs <= 0) return null
        if (lastForegroundEndEpochMs != null || devicePausedAtEpochMs != null) return null
        return nowEpochMs + remainingSessionMs
    }

    fun displayedSessionRemainingMs(
        nowEpochMs: Long,
        sessionDeadlineEpochMs: Long?,
        pausedRemainingMs: Long?,
    ): Long? {
        if (sessionDeadlineEpochMs != null) {
            return (sessionDeadlineEpochMs - nowEpochMs).coerceAtLeast(0).takeIf { it > 0 }
        }
        return pausedRemainingMs?.takeIf { it > 0 }
    }
}
