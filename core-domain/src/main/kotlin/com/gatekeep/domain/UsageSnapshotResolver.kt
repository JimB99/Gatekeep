package com.gatekeep.domain

import com.gatekeep.domain.model.UsageSnapshot

object UsageSnapshotResolver {

    /**
     * UsageStats is authoritative when it reports usage for a period.
     * Persisted aggregates can lag or inflate (e.g. repeated sync snapshots); only
     * fall back to persisted totals when stats report zero.
     */
    fun merge(stats: UsageSnapshot, persisted: UsageSnapshot): UsageSnapshot = UsageSnapshot(
        dailyMs = mergePeriodMs(stats.dailyMs, persisted.dailyMs),
        hourlyMs = mergePeriodMs(stats.hourlyMs, persisted.hourlyMs),
        weeklyMs = mergePeriodMs(stats.weeklyMs, persisted.weeklyMs),
    )

    /**
     * UsageStats is authoritative when it reports usage for a period.
     * When stats report zero, keep persisted totals (transient query gaps must not erase usage).
     */
    fun mergePeriodMs(statsMs: Long, persistedMs: Long): Long =
        if (statsMs > 0L) statsMs else persistedMs

    /**
     * HUD / live refresh: advance when stats report usage; hold last displayed value when stats briefly report zero.
     */
    fun applyLivePeriodMs(displayedMs: Long?, liveStatsMs: Long?): Long? {
        if (liveStatsMs == null) return displayedMs
        if (liveStatsMs > 0L) return liveStatsMs
        return displayedMs ?: liveStatsMs
    }
}
