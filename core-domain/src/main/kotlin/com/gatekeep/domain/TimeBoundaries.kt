package com.gatekeep.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields

data class TimeRange(
    val startMs: Long,
    val endExclusiveMs: Long,
) {
    val durationMs: Long get() = (endExclusiveMs - startMs).coerceAtLeast(0)
}

object TimeBoundaries {

    fun dayBounds(anchorMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): TimeRange {
        val start = dayStartEpochMs(anchorMs, zoneId)
        val nextDay = ZonedDateTime.ofInstant(Instant.ofEpochMilli(start), zoneId).plusDays(1)
        return TimeRange(start, nextDay.toInstant().toEpochMilli())
    }

    fun weekBounds(
        anchorMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        weekFields: WeekFields = WeekFields.ISO,
    ): TimeRange {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchorMs), zoneId)
        val start = zdt.with(weekFields.dayOfWeek(), 1)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(start), zoneId)
            .plusWeeks(1)
            .toInstant()
            .toEpochMilli()
        return TimeRange(start, end)
    }

    fun monthBounds(year: Int, month: Int, zoneId: ZoneId = ZoneId.systemDefault()): TimeRange {
        val start = LocalDate.of(year, month, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = LocalDate.of(year, month, 1)
            .plusMonths(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return TimeRange(start, end)
    }

    fun yearBounds(year: Int, zoneId: ZoneId = ZoneId.systemDefault()): TimeRange {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return TimeRange(start, end)
    }

    fun dayStartEpochMs(anchorMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchorMs), zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    fun hourStartEpochMs(anchorMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchorMs), zoneId)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()

    fun hourBounds(anchorMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): TimeRange {
        val start = hourStartEpochMs(anchorMs, zoneId)
        val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(start), zoneId)
            .plusHours(1)
            .toInstant()
            .toEpochMilli()
        return TimeRange(start, end)
    }

    fun dayOffsetsFrom(startDayMs: Long, offset: Int, zoneId: ZoneId = ZoneId.systemDefault()): TimeRange {
        val startZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startDayMs), zoneId)
        val dayStart = startZdt.plusDays(offset.toLong())
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val dayEnd = dayStartZdt(dayStart, zoneId).plusDays(1).toInstant().toEpochMilli()
        return TimeRange(dayStart, dayEnd)
    }

    fun daysInMonth(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).lengthOfMonth()

    fun iterateDaysInRange(range: TimeRange, zoneId: ZoneId = ZoneId.systemDefault()): List<TimeRange> {
        val days = mutableListOf<TimeRange>()
        var cursor = dayStartEpochMs(range.startMs, zoneId)
        while (cursor < range.endExclusiveMs) {
            val dayEnd = ZonedDateTime.ofInstant(Instant.ofEpochMilli(cursor), zoneId)
                .plusDays(1)
                .toInstant()
                .toEpochMilli()
            val effectiveEnd = minOf(dayEnd, range.endExclusiveMs)
            if (effectiveEnd > cursor) {
                days.add(TimeRange(cursor, effectiveEnd))
            }
            cursor = dayEnd
        }
        return days
    }

    fun iterateHoursInDay(dayStartMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): List<TimeRange> {
        val dayEnd = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dayStartMs), zoneId)
            .plusDays(1)
            .toInstant()
            .toEpochMilli()
        val hours = mutableListOf<TimeRange>()
        var cursor = dayStartMs
        while (cursor < dayEnd) {
            val hourEnd = ZonedDateTime.ofInstant(Instant.ofEpochMilli(cursor), zoneId)
                .plusHours(1)
                .toInstant()
                .toEpochMilli()
            hours.add(TimeRange(cursor, minOf(hourEnd, dayEnd)))
            cursor = hourEnd
        }
        return hours
    }

    private fun dayStartZdt(dayStartMs: Long, zoneId: ZoneId): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(dayStartMs), zoneId)
}
