package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageBucketAggregatorTest {

    @Test
    fun `clipDuration respects bucket bounds`() {
        val clipped = UsageBucketAggregator.clipDuration(
            sessionStartMs = 1000,
            sessionEndMs = 5000,
            bucketStartMs = 2000,
            bucketEndMs = 4000,
        )
        assertEquals(2000, clipped)
    }

    @Test
    fun `allocateSessionsToBuckets splits across buckets`() {
        val sessions = listOf(
            UsageBucketAggregator.ForegroundSession(3_500_000, 7_500_000),
        )
        val totals = UsageBucketAggregator.allocateSessionsToBuckets(
            sessions,
            bucketStarts = longArrayOf(0, 3_600_000, 7_200_000),
            bucketEnds = longArrayOf(3_600_000, 7_200_000, 10_800_000),
        )
        assertArrayEquals(longArrayOf(100_000, 3_600_000, 300_000), totals)
    }

    @Test
    fun `computeScaleMs uses average of non-zero buckets with floor`() {
        val scale = UsageBucketAggregator.computeScaleMs(longArrayOf(0, 120_000, 180_000))
        assertEquals(150_000, scale)
    }
}

class TrackedAppMergeTest {

    @Test
    fun `mergeDailyLimit picks stricter non-null cap`() {
        assertEquals(2L * 60 * 60 * 1000, TrackedAppMerge.mergeDailyLimit(3L * 60 * 60 * 1000, 2L * 60 * 60 * 1000))
    }

    @Test
    fun `mergeDailyLimit keeps existing when incoming null`() {
        assertEquals(5L, TrackedAppMerge.mergeDailyLimit(5L, null))
    }

    @Test
    fun `mergeDailyLimit uses incoming when existing null`() {
        assertEquals(7L, TrackedAppMerge.mergeDailyLimit(null, 7L))
    }
}

class StatsPeriodLogicTest {

    private val zone = ZoneId.of("Europe/Amsterdam")

    @Test
    fun `cannot shift forward from current day`() {
        val now = ZonedDateTime.of(2026, 8, 28, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertFalse(StatsPeriodLogic.canShiftForward(StatsPeriodKind.day, now, now, zone))
    }

    @Test
    fun `can shift forward from previous week`() {
        val now = ZonedDateTime.of(2026, 8, 28, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val previousWeek = ZonedDateTime.of(2026, 8, 14, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(StatsPeriodLogic.canShiftForward(StatsPeriodKind.week, previousWeek, now, zone))
    }

    @Test
    fun `shift forward from previous month lands in current month`() {
        val now = ZonedDateTime.of(2026, 8, 28, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val july = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val shifted = StatsPeriodLogic.shiftAnchor(
            StatsPeriodKind.month,
            july,
            forward = true,
            nowMs = now,
            zoneId = zone,
        )
        assertEquals(
            StatsPeriodLogic.periodStartMs(StatsPeriodKind.month, now, zone),
            StatsPeriodLogic.periodStartMs(StatsPeriodKind.month, shifted, zone),
        )
    }

    @Test
    fun `shift forward no-op when already at current period`() {
        val now = ZonedDateTime.of(2026, 8, 28, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val shifted = StatsPeriodLogic.shiftAnchor(
            StatsPeriodKind.day,
            now,
            forward = true,
            nowMs = now,
            zoneId = zone,
        )
        assertEquals(now, shifted)
    }
}
