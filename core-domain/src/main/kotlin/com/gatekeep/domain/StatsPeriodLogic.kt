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

    fun periodStartMs(kind: StatsPeriodKind, anchorMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchorMs), zoneId)
        return when (kind) {
            StatsPeriodKind.day -> zdt.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            StatsPeriodKind.week -> {
                zdt.with(WeekFields.ISO.dayOfWeek(), 1)
                    .toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
            StatsPeriodKind.month -> zdt.withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            StatsPeriodKind.year -> zdt.withDayOfYear(1).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
    }

    fun canShiftForward(kind: StatsPeriodKind, anchorMs: Long, nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val currentStart = periodStartMs(kind, nowMs, zoneId)
        val displayedStart = periodStartMs(kind, anchorMs, zoneId)
        return displayedStart < currentStart
    }

    fun shiftAnchor(
        kind: StatsPeriodKind,
        anchorMs: Long,
        forward: Boolean,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (forward && !canShiftForward(kind, anchorMs, nowMs, zoneId)) {
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
        if (forward && periodStartMs(kind, shiftedMs, zoneId) > periodStartMs(kind, nowMs, zoneId)) {
            return nowMs
        }
        return shiftedMs
    }
}
