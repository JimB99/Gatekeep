package com.gatekeep.domain

object ScheduleConflictChecker {

    fun rangesOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean {
        if (startA == endA || startB == endB) return false
        return minuteSpans(startA, endA).any { spanA ->
            minuteSpans(startB, endB).any { spanB -> spansOverlap(spanA, spanB) }
        }
    }

    fun wouldConflict(
        existing: List<com.gatekeep.domain.model.ScheduleWindow>,
        dayOfWeek: Int,
        startMinute: Int,
        endMinute: Int,
        excludeIds: Set<Long> = emptySet(),
    ): Boolean = existing.any { window ->
        window.id !in excludeIds &&
            !window.isProfileAutoSwitch &&
            window.dayOfWeek == dayOfWeek &&
            rangesOverlap(startMinute, endMinute, window.startMinute, window.endMinute)
    }

    fun conflictingDays(
        existing: List<com.gatekeep.domain.model.ScheduleWindow>,
        days: Set<Int>,
        startMinute: Int,
        endMinute: Int,
    ): Set<Int> = days.filter { day ->
        wouldConflict(existing, day, startMinute, endMinute)
    }.toSet()

    fun addableDays(
        existing: List<com.gatekeep.domain.model.ScheduleWindow>,
        days: Set<Int>,
        startMinute: Int,
        endMinute: Int,
    ): Set<Int> = days - conflictingDays(existing, days, startMinute, endMinute)

    private fun minuteSpans(start: Int, end: Int): List<IntRange> =
        if (start < end) {
            listOf(start until end)
        } else {
            listOf(start until MINUTES_PER_DAY, 0 until end)
        }

    private fun spansOverlap(a: IntRange, b: IntRange): Boolean = a.first < b.last && b.first < a.last

    private const val MINUTES_PER_DAY = 24 * 60
}
