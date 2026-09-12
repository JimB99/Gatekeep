package com.gatekeep.domain

import com.gatekeep.domain.model.ScheduleWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleTestWindows {

    fun aroundNow(
        profileId: Long,
        segmentId: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
        marginMinutes: Int = 120,
    ): ScheduleWindow {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), zoneId)
        val dayOfWeek = now.dayOfWeek.value
        val nowMinute = now.hour * 60 + now.minute
        val startMinute = (nowMinute - marginMinutes).coerceAtLeast(0)
        val endMinute = (nowMinute + marginMinutes).coerceAtMost(24 * 60 - 1)
        return ScheduleWindow(
            profileId = profileId,
            segmentId = segmentId,
            dayOfWeek = dayOfWeek,
            startMinute = startMinute,
            endMinute = endMinute,
        )
    }

    fun excludingNow(
        profileId: Long,
        segmentId: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduleWindow {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), zoneId)
        val dayOfWeek = now.dayOfWeek.value
        val nowMinute = now.hour * 60 + now.minute
        val (startMinute, endMinute) = if (nowMinute < 12 * 60) {
            val start = (nowMinute + 180).coerceAtMost(23 * 60)
            val end = (start + 60).coerceAtMost(24 * 60 - 1)
            start to end
        } else {
            0 to 59
        }
        return ScheduleWindow(
            profileId = profileId,
            segmentId = segmentId,
            dayOfWeek = dayOfWeek,
            startMinute = startMinute,
            endMinute = endMinute,
        )
    }
}
