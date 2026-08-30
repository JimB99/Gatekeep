package com.gatekeep.domain

import com.gatekeep.domain.model.ScheduleWindow

object ScheduleWindowMatcher {

    fun isMinuteInWindow(
        dayOfWeek: Int,
        minuteOfDay: Int,
        windowDay: Int,
        startMinute: Int,
        endMinute: Int,
    ): Boolean {
        if (startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            dayOfWeek == windowDay &&
                minuteOfDay >= startMinute &&
                minuteOfDay < endMinute
        } else {
            (dayOfWeek == windowDay && minuteOfDay >= startMinute) ||
                (dayOfWeek == nextDay(windowDay) && minuteOfDay < endMinute)
        }
    }

    fun matchesWindow(window: ScheduleWindow, dayOfWeek: Int, minuteOfDay: Int): Boolean =
        isMinuteInWindow(
            dayOfWeek = dayOfWeek,
            minuteOfDay = minuteOfDay,
            windowDay = window.dayOfWeek,
            startMinute = window.startMinute,
            endMinute = window.endMinute,
        )

    fun isOvernight(startMinute: Int, endMinute: Int): Boolean = endMinute <= startMinute

    private fun nextDay(day: Int): Int = (day + 1) % 7
}
