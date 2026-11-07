package com.gatekeep.app.ui.components

/**
 * Pure rules for [ForegroundPeriodicRefreshEffect] (unit-tested; composable stays thin).
 */
object ForegroundPeriodicRefreshRules {
    const val DEFAULT_INTERVAL_MS = 1_000L

    private const val MIN_INTERVAL_MS = 100L

    fun shouldSchedule(enabled: Boolean): Boolean = enabled

    fun coercedIntervalMs(intervalMs: Long): Long = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)
}
