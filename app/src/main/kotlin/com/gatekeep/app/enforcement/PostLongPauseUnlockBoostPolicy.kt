package com.gatekeep.app.enforcement

/**
 * After a long screen-off pause, unlock often hits [UnlockOverlayPolicy] skip (home in front).
 * A short reconcile boost helps Usage Stats and window polling adopt the next monitored app
 * without evaluating the last monitored app on unlock (RES-08).
 */
object PostLongPauseUnlockBoostPolicy {

    const val LONG_DEVICE_PAUSE_MS = 60 * 60 * 1000L
    const val BOOST_DURATION_MS = 90_000L
    const val BOOST_RECONCILE_INTERVAL_MS = 750L

    fun shouldStartBoost(pauseDurationMs: Long, unlockSkipped: Boolean): Boolean {
        if (!unlockSkipped) return false
        return pauseDurationMs >= LONG_DEVICE_PAUSE_MS
    }

    fun pauseDurationMs(nowEpochMs: Long, pausedAtEpochMs: Long?): Long? {
        if (pausedAtEpochMs == null || pausedAtEpochMs <= 0L) return null
        return (nowEpochMs - pausedAtEpochMs).coerceAtLeast(0L)
    }

    fun boostActive(nowEpochMs: Long, boostStartedAtEpochMs: Long): Boolean {
        if (boostStartedAtEpochMs <= 0L) return false
        return nowEpochMs - boostStartedAtEpochMs < BOOST_DURATION_MS
    }

    fun shouldScheduleNextReconcile(
        nowEpochMs: Long,
        boostStartedAtEpochMs: Long,
        lastReconcileAtEpochMs: Long,
        reconcileIntervalMs: Long = BOOST_RECONCILE_INTERVAL_MS,
    ): Boolean {
        if (!boostActive(nowEpochMs, boostStartedAtEpochMs)) return false
        return nowEpochMs - lastReconcileAtEpochMs >= reconcileIntervalMs
    }
}
