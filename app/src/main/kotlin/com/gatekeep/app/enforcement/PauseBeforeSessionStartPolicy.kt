package com.gatekeep.app.enforcement

/**
 * SessionStartDecision must see the lock pause marker. Resuming first clears
 * devicePausedAtEpochMs and a live in-memory session Continues (RES-06).
 * Do not start or resume a session while the device is still locked.
 */
object PauseBeforeSessionStartPolicy {

    fun shouldHoldSessionStart(livePaused: Boolean): Boolean = livePaused

    /**
     * Prefs pause to copy onto the session before [SessionStartDecision], so a long
     * lock is visible as away time. Leave already froze the clock via lastForegroundEnd;
     * stamping pause there would double-exclude on resume.
     */
    fun pauseAtToStamp(
        sessionPausedAtEpochMs: Long?,
        prefsPausedAtEpochMs: Long?,
        lastForegroundEndEpochMs: Long?,
    ): Long? {
        if (lastForegroundEndEpochMs != null) return null
        if (sessionPausedAtEpochMs != null) return null
        return prefsPausedAtEpochMs
    }
}
