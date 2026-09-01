package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale

class TimeBoundariesTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    @Test
    fun springForwardDayIs23Hours() {
        val springForward = LocalDate.of(2026, 3, 29)
            .atStartOfDay(amsterdam)
            .toInstant()
            .toEpochMilli()
        val bounds = TimeBoundaries.dayBounds(springForward, amsterdam)
        assertEquals(23L * 3_600_000L, bounds.durationMs)
    }

    @Test
    fun fallBackDayIs25Hours() {
        val fallBack = LocalDate.of(2026, 10, 25)
            .atStartOfDay(amsterdam)
            .toInstant()
            .toEpochMilli()
        val bounds = TimeBoundaries.dayBounds(fallBack, amsterdam)
        assertEquals(25L * 3_600_000L, bounds.durationMs)
    }

    @Test
    fun noLimitTodayEndsAtNextLocalMidnightSpringForward() {
        val noon = ZonedDateTime.of(2026, 3, 29, 12, 0, 0, 0, amsterdam)
            .toInstant()
            .toEpochMilli()
        val bounds = TimeBoundaries.dayBounds(noon, amsterdam)
        val nextMidnight = ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, amsterdam)
            .toInstant()
            .toEpochMilli()
        assertEquals(nextMidnight, bounds.endExclusiveMs)
        assertNotEquals(bounds.startMs + 86_400_000L, bounds.endExclusiveMs)
    }

    @Test
    fun monthBucketCountMatchesCalendarDays() {
        val feb2026 = TimeBoundaries.monthBounds(2026, 2, amsterdam)
        val days = TimeBoundaries.iterateDaysInRange(feb2026, amsterdam)
        assertEquals(28, days.size)
        assertEquals(28, TimeBoundaries.daysInMonth(2026, 2))
    }

    @Test
    fun weekBoundsUseSameWeekFieldsAsLabel() {
        val locale = Locale.forLanguageTag("en-GB")
        val weekFields = WeekFields.of(locale)
        val anchor = ZonedDateTime.of(2026, 1, 7, 12, 0, 0, 0, amsterdam)
            .toInstant()
            .toEpochMilli()
        val bounds = TimeBoundaries.weekBounds(anchor, amsterdam, weekFields)
        val startDate = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(bounds.startMs),
            amsterdam,
        ).toLocalDate()
        assertEquals(weekFields.firstDayOfWeek, startDate.dayOfWeek)
        assertEquals(7, TimeBoundaries.iterateDaysInRange(bounds, amsterdam).size)
    }

    @Test
    fun hourBucketsCoverFullDayAcrossDstSpringForward() {
        val springForward = LocalDate.of(2026, 3, 29)
            .atStartOfDay(amsterdam)
            .toInstant()
            .toEpochMilli()
        val hours = TimeBoundaries.iterateHoursInDay(springForward, amsterdam)
        assertEquals(23, hours.size)
        val totalMs = hours.sumOf { it.durationMs }
        assertEquals(23L * 3_600_000L, totalMs)
    }

    @Test
    fun hourBucketsCoverFullDayAcrossDstFallBack() {
        val fallBack = LocalDate.of(2026, 10, 25)
            .atStartOfDay(amsterdam)
            .toInstant()
            .toEpochMilli()
        val hours = TimeBoundaries.iterateHoursInDay(fallBack, amsterdam)
        assertEquals(25, hours.size)
        val totalMs = hours.sumOf { it.durationMs }
        assertEquals(25L * 3_600_000L, totalMs)
    }

    @Test
    fun dayOffsetsFromHandlesDst() {
        val springForward = LocalDate.of(2026, 3, 29)
            .atStartOfDay(amsterdam)
            .toInstant()
            .toEpochMilli()
        val next = TimeBoundaries.dayOffsetsFrom(springForward, 1, amsterdam)
        assertEquals(
            LocalDate.of(2026, 3, 30).atStartOfDay(amsterdam).toInstant().toEpochMilli(),
            next.startMs,
        )
    }

    @Test
    fun yearBoundsSpanCalendarYear() {
        val bounds = TimeBoundaries.yearBounds(2026, amsterdam)
        assertEquals(
            LocalDate.of(2026, 1, 1).atStartOfDay(amsterdam).toInstant().toEpochMilli(),
            bounds.startMs,
        )
        assertEquals(
            LocalDate.of(2027, 1, 1).atStartOfDay(amsterdam).toInstant().toEpochMilli(),
            bounds.endExclusiveMs,
        )
        assertEquals(365, TimeBoundaries.iterateDaysInRange(bounds, amsterdam).size)
    }
}
