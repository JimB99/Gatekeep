package com.gatekeep.domain

import com.gatekeep.domain.model.UsagePeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

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

    @Test
    fun `aggregateSessions buckets pre-dawn usage into previous usage day`() {
        val zone = ZoneId.of("Europe/Amsterdam")
        val reset = 4 * 60
        val twoAm = ZonedDateTime.of(2026, 10, 1, 2, 0, 0, 0, zone)
        val sessionStart = twoAm.toInstant().toEpochMilli()
        val sessionEnd = twoAm.plusMinutes(10).toInstant().toEpochMilli()
        val daily = UsageAggregator.aggregateSessions(
            listOf(UsageSessionRecord("com.example", 1L, sessionStart, sessionEnd)),
            zone,
            reset,
        ).first { it.period == UsagePeriod.day }
        assertEquals(
            ZonedDateTime.of(2026, 9, 30, 4, 0, 0, 0, zone).toInstant().toEpochMilli(),
            daily.periodStartEpochMs,
        )
        assertEquals(10 * 60_000L, daily.totalMs)
    }
}
