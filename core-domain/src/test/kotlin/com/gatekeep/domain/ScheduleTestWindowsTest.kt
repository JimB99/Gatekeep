package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTestWindowsTest {

    @Test
    fun aroundNow_includesCurrentMinute() {
        val window = ScheduleTestWindows.aroundNow(profileId = 1L, segmentId = 2L)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val nowMinute = now.hour * 60 + now.minute
        assertTrue(window.dayOfWeek == now.dayOfWeek.value)
        assertTrue(nowMinute in window.startMinute..window.endMinute)
    }

    @Test
    fun excludingNow_doesNotIncludeCurrentMinute() {
        val window = ScheduleTestWindows.excludingNow(profileId = 1L, segmentId = 2L)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val nowMinute = now.hour * 60 + now.minute
        assertTrue(nowMinute !in window.startMinute..window.endMinute)
    }
}
