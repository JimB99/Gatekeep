package com.gatekeep.domain

import com.gatekeep.domain.model.UsagePeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UsageAggregatorSyncTest {

    @Test
    fun `repeated full day session snapshots sum and inflate persisted totals`() {
        val dayStart = 1_700_000_000_000L
        val usageMs = 14 * 60_000L
        val sessions = listOf(
            UsageSessionRecord("com.example", 1L, dayStart, dayStart + usageMs),
            UsageSessionRecord("com.example", 1L, dayStart, dayStart + usageMs),
            UsageSessionRecord("com.example", 1L, dayStart, dayStart + usageMs),
        )
        val dailyTotal = UsageAggregator.aggregateSessions(sessions)
            .first { it.period == UsagePeriod.day }
            .totalMs
        assertEquals(42 * 60_000L, dailyTotal)
    }
}
