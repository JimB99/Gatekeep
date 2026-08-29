package com.gatekeep.domain

/**
 * Chooses how often the enforcement/notification loop should tick while a monitored app is open.
 */
object EnforcementPollInterval {
    const val FINE_INTERVAL_MS = 1_000L
    const val COARSE_INTERVAL_MS = 30_000L
    const val CRITICAL_THRESHOLD_MS = 5 * 60_000L

    /**
     * @return null when there are no active deadlines (loop should stop if app left foreground).
     */
    fun enforcementLoopIntervalMs(nowMs: Long, deadlineEpochMs: List<Long?>): Long? {
        val deadlines = deadlineEpochMs.filterNotNull()
        if (deadlines.isEmpty()) return null

        val minRemaining = deadlines
            .map { (it - nowMs).coerceAtLeast(0) }
            .minOrNull()
            ?: return FINE_INTERVAL_MS

        return if (minRemaining <= CRITICAL_THRESHOLD_MS) {
            FINE_INTERVAL_MS
        } else {
            COARSE_INTERVAL_MS
        }
    }
}
