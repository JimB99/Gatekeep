package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHudLinesTest {

    @Test
    fun `hides notification only when nothing to show`() {
        assertTrue(UsageHudInfo().countdownLines().isEmpty())
    }

    @Test
    fun `shows daily usage with unlimited cap when remaining is missing`() {
        val lines = UsageHudInfo(
            dailyUsedMs = 12 * 60_000L,
            dailyLimitMs = null,
        ).countdownLines()

        assertEquals(
            listOf(
                UsageHudLine.UsedOverLimit(UsageHudBucket.daily, 12 * 60_000L, null),
            ),
            lines,
        )
    }

    @Test
    fun `shows hourly and weekly usage without remaining`() {
        val lines = UsageHudInfo(
            hourlyUsedMs = 10 * 60_000L,
            hourlyLimitMs = null,
            weeklyUsedMs = 40 * 60_000L,
            weeklyLimitMs = 5 * 60 * 60_000L,
        ).countdownLines()

        assertEquals(
            listOf(
                UsageHudLine.UsedOverLimit(UsageHudBucket.hourly, 10 * 60_000L, null),
                UsageHudLine.UsedOverLimit(UsageHudBucket.weekly, 40 * 60_000L, 5 * 60 * 60_000L),
            ),
            lines,
        )
    }

    @Test
    fun `keeps session remaining when present`() {
        val lines = UsageHudInfo(
            sessionRemainingMs = 90_000L,
            dailyUsedMs = 5 * 60_000L,
            dailyLimitMs = 30 * 60_000L,
        ).countdownLines()

        assertEquals(UsageHudLine.Session(90_000L), lines.first())
        assertEquals(2, lines.size)
    }

    @Test
    fun `ticks used from remaining when a countdown is active`() {
        assertEquals(
            20 * 60_000L,
            tickHudUsedMs(
                currentUsedMs = 10 * 60_000L,
                remainingMs = 10 * 60_000L,
                limitMs = 30 * 60_000L,
                elapsedMs = 1_000L,
            ),
        )
    }

    @Test
    fun `advances used while remaining is missing such as extension grace`() {
        assertEquals(
            11 * 60_000L,
            tickHudUsedMs(
                currentUsedMs = 10 * 60_000L,
                remainingMs = null,
                limitMs = 15 * 60_000L,
                elapsedMs = 60_000L,
            ),
        )
    }
}
