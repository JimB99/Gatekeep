package com.gatekeep.domain

import com.gatekeep.domain.model.ScheduleWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduleWindowGrouperTest {

    @Test
    fun `groups windows with same time range`() {
        val windows = listOf(
            ScheduleWindow(id = 1, profileId = 1, dayOfWeek = 1, startMinute = 540, endMinute = 1020),
            ScheduleWindow(id = 2, profileId = 1, dayOfWeek = 2, startMinute = 540, endMinute = 1020),
            ScheduleWindow(id = 3, profileId = 1, dayOfWeek = 3, startMinute = 600, endMinute = 1020),
        )
        val grouped = ScheduleWindowGrouper.group(windows)
        assertEquals(2, grouped.size)
        assertEquals(setOf(1, 2), grouped[0].days)
        assertEquals(setOf(3), grouped[1].days)
    }
}

class ScheduleConflictCheckerTest {

    @Test
    fun `detects overlapping windows on same day`() {
        val existing = listOf(
            ScheduleWindow(id = 1, profileId = 1, dayOfWeek = 1, startMinute = 540, endMinute = 1020),
        )
        assertTrue(
            ScheduleConflictChecker.wouldConflict(existing, 1, 600, 900),
        )
    }

    @Test
    fun `allows adjacent non overlapping windows`() {
        val existing = listOf(
            ScheduleWindow(id = 1, profileId = 1, dayOfWeek = 1, startMinute = 540, endMinute = 720),
        )
        assertFalse(
            ScheduleConflictChecker.wouldConflict(existing, 1, 720, 1020),
        )
    }

    @Test
    fun `allows same time on different days`() {
        val existing = listOf(
            ScheduleWindow(id = 1, profileId = 1, dayOfWeek = 1, startMinute = 540, endMinute = 1020),
        )
        assertFalse(
            ScheduleConflictChecker.wouldConflict(existing, 2, 540, 1020),
        )
    }

    @Test
    fun `ignores auto switch windows when checking conflicts`() {
        val existing = listOf(
            ScheduleWindow(
                id = 1,
                profileId = 1,
                dayOfWeek = 1,
                startMinute = 540,
                endMinute = 1020,
                isProfileAutoSwitch = true,
            ),
        )
        assertFalse(
            ScheduleConflictChecker.wouldConflict(existing, 1, 540, 1020),
        )
    }
}

class ChartAxisTicksTest {

    @Test
    fun `omits zero tick when there is usage`() {
        val ticks = UsageBucketAggregator.computeChartAxisTicks(scaleMs = 90 * 60_000L, maxUsageMs = 30 * 60_000L)
        assertTrue(ticks.all { it > 0 })
    }

    @Test
    fun `snaps to fifteen minute steps`() {
        assertEquals(15 * 60_000L, UsageBucketAggregator.snapToTickStep(10 * 60_000L))
        assertEquals(30 * 60_000L, UsageBucketAggregator.snapToTickStep(25 * 60_000L))
    }
}
