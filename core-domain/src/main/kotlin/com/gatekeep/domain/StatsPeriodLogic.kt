package com.gatekeep.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields

enum class StatsPeriodKind {
    day,
    week,
    month,
    year,
}

object StatsPeriodLogic {

    fun periodStartMs(
        kind: StatsPeriodKind,
        anchorMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        dayResetMinuteOfDay: Int = 0,
    ): Long {
        val date = TimeBoundaries.usageDate(anchorMs, zoneId, dayResetMinuteOfDay)
        return when (kind) {
            StatsPeriodKind.day -> TimeBoundaries.dayStartEpochMs(anchorMs, zoneId, dayResetMinuteOfDay)
            StatsPeriodKind.week -> TimeBoundaries.weekBounds(
                anchorMs,
                zoneId,
                WeekFields.ISO,
                dayResetMinuteOfDay,
            ).startMs
            StatsPeriodKind.month -> TimeBoundaries.monthBounds(
                date.year,
                date.monthValue,
                zoneId,
                dayResetMinuteOfDay,
            ).startMs
            StatsPeriodKind.year -> TimeBoundaries.yearBounds(date.year, zoneId, dayResetMinuteOfDay).startMs
        }
    }

    fun canShiftForward(
        kind: StatsPeriodKind,
        anchorMs: Long,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        dayResetMinuteOfDay: Int = 0,
    ): Boolean {
        val currentStart = periodStartMs(kind, nowMs, zoneId, dayResetMinuteOfDay)
        val displayedStart = periodStartMs(kind, anchorMs, zoneId, dayResetMinuteOfDay)
        return displayedStart < currentStart
    }

    fun shiftAnchor(
        kind: StatsPeriodKind,
        anchorMs: Long,
        forward: Boolean,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        dayResetMinuteOfDay: Int = 0,
    ): Long {
        if (forward && !canShiftForward(kind, anchorMs, nowMs, zoneId, dayResetMinuteOfDay)) {
            return anchorMs
        }
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchorMs), zoneId)
        val shifted = when (kind) {
            StatsPeriodKind.day -> zdt.plusDays(if (forward) 1 else -1)
            StatsPeriodKind.week -> zdt.plusWeeks(if (forward) 1 else -1)
            StatsPeriodKind.month -> zdt.plusMonths(if (forward) 1 else -1)
            StatsPeriodKind.year -> zdt.plusYears(if (forward) 1 else -1)
        }
        val shiftedMs = shifted.toInstant().toEpochMilli()
        if (forward && periodStartMs(kind, shiftedMs, zoneId, dayResetMinuteOfDay) >
            periodStartMs(kind, nowMs, zoneId, dayResetMinuteOfDay)
        ) {
            return nowMs
        }
        return shiftedMs
    }
}
