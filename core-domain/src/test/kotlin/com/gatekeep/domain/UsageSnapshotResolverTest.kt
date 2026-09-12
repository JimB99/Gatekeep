package com.gatekeep.domain

import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UsageSnapshotResolverTest {

    @Test
    fun `prefers usage stats when persisted aggregate is inflated`() {
        val merged = UsageSnapshotResolver.merge(
            stats = UsageSnapshot(dailyMs = 14 * 60_000L, hourlyMs = 5 * 60_000L, weeklyMs = 20 * 60_000L),
            persisted = UsageSnapshot(dailyMs = 43 * 60_000L, hourlyMs = 10 * 60_000L, weeklyMs = 50 * 60_000L),
        )
        assertEquals(14 * 60_000L, merged.dailyMs)
        assertEquals(5 * 60_000L, merged.hourlyMs)
        assertEquals(20 * 60_000L, merged.weeklyMs)
    }

    @Test
    fun `falls back to persisted totals when stats report zero`() {
        val merged = UsageSnapshotResolver.merge(
            stats = UsageSnapshot(),
            persisted = UsageSnapshot(dailyMs = 12 * 60_000L, hourlyMs = 3 * 60_000L, weeklyMs = 40 * 60_000L),
        )
        assertEquals(12 * 60_000L, merged.dailyMs)
        assertEquals(3 * 60_000L, merged.hourlyMs)
        assertEquals(40 * 60_000L, merged.weeklyMs)
    }
}
