package com.gatekeep.domain

import com.gatekeep.domain.model.ScheduleWindow

object ScheduleConflictChecker {

    fun rangesOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean =
        startA < endB && startB < endA

    fun wouldConflict(
        existing: List<ScheduleWindow>,
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
        existing: List<ScheduleWindow>,
        days: Set<Int>,
        startMinute: Int,
        endMinute: Int,
    ): Set<Int> = days.filter { day ->
        wouldConflict(existing, day, startMinute, endMinute)
    }.toSet()
}
