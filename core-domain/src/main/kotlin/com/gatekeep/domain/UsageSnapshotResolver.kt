package com.gatekeep.domain

import com.gatekeep.domain.model.UsageSnapshot

object UsageSnapshotResolver {

    /**
     * UsageStats is authoritative when it reports usage for a period.
     * Persisted aggregates can lag or inflate (e.g. repeated sync snapshots); only
     * fall back to persisted totals when stats report zero.
     */
    fun merge(stats: UsageSnapshot, persisted: UsageSnapshot): UsageSnapshot = UsageSnapshot(
        dailyMs = preferStats(stats.dailyMs, persisted.dailyMs),
        hourlyMs = preferStats(stats.hourlyMs, persisted.hourlyMs),
        weeklyMs = preferStats(stats.weeklyMs, persisted.weeklyMs),
    )

    private fun preferStats(statsMs: Long, persistedMs: Long): Long =
        if (statsMs > 0L) statsMs else persistedMs
}
