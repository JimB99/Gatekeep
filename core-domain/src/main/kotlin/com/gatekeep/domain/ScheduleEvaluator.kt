package com.gatekeep.domain

import com.gatekeep.domain.model.ScheduleWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleEvaluator {

    fun isWithinAllowedWindow(
        windows: List<ScheduleWindow>,
        packageName: String,
        profileId: Long,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val applicable = windows.filter { window ->
            window.profileId == profileId &&
                (window.packageName == null || window.packageName == packageName)
        }
        if (applicable.isEmpty()) return true

        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMs), zoneId)
        val dayOfWeek = now.dayOfWeek.value % 7
        val minuteOfDay = now.hour * 60 + now.minute

        return applicable.any { window ->
            window.dayOfWeek == dayOfWeek &&
                minuteOfDay >= window.startMinute &&
                minuteOfDay < window.endMinute
        }
    }

    fun activeProfileIdForAutoSchedule(
        windows: List<ScheduleWindow>,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val autoWindows = windows.filter { it.isProfileAutoSwitch }
        if (autoWindows.isEmpty()) return null

        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMs), zoneId)
        val dayOfWeek = now.dayOfWeek.value % 7
        val minuteOfDay = now.hour * 60 + now.minute

        return autoWindows
            .filter { window ->
                window.dayOfWeek == dayOfWeek &&
                    minuteOfDay >= window.startMinute &&
                    minuteOfDay < window.endMinute
            }
            .maxByOrNull { it.startMinute }
            ?.profileId
    }
}
