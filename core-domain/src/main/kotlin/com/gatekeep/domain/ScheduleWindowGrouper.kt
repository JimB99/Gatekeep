package com.gatekeep.domain

import com.gatekeep.domain.model.ScheduleWindow

data class GroupedScheduleWindow(
    val windowIds: List<Long>,
    val days: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
)

object ScheduleWindowGrouper {

    fun group(windows: List<ScheduleWindow>): List<GroupedScheduleWindow> =
        windows
            .groupBy { window -> window.startMinute to window.endMinute }
            .map { (_, group) ->
                GroupedScheduleWindow(
                    windowIds = group.map { it.id },
                    days = group.map { it.dayOfWeek }.toSet(),
                    startMinute = group.first().startMinute,
                    endMinute = group.first().endMinute,
                )
            }
            .sortedWith(compareBy({ it.startMinute }, { it.endMinute }))
}
