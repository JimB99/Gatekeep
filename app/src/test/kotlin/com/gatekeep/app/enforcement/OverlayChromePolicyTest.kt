package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OverlayChromePolicyTest {

    @Test
    fun onOpen_isDistinctFromSessionAndPeriod() {
        val chrome = OverlayChromePolicy.chrome(BlockPresentationReason.openGate)

        assertEquals(OverlayAccentFamily.OnOpen, chrome.accent)
        assertEquals(OverlayChromeTitle.OnOpenWait, chrome.title)
        assertNotEquals(OverlayChromePolicy.chrome(BlockPresentationReason.sessionLimit).accent, chrome.accent)
        assertNotEquals(OverlayChromePolicy.chrome(BlockPresentationReason.dailyLimit).accent, chrome.accent)
    }

    @Test
    fun session_isDistinctFromPeriod() {
        val session = OverlayChromePolicy.chrome(BlockPresentationReason.sessionLimit)
        val daily = OverlayChromePolicy.chrome(BlockPresentationReason.dailyLimit)

        assertEquals(OverlayAccentFamily.Session, session.accent)
        assertEquals(OverlayChromeTitle.SessionLimit, session.title)
        assertEquals(OverlayAccentFamily.Period, daily.accent)
        assertNotEquals(session.accent, daily.accent)
        assertNotEquals(session.title, daily.title)
    }

    @Test
    fun dailyHourlyWeekly_sharePeriodLook_titlesNameThePeriod() {
        val daily = OverlayChromePolicy.chrome(BlockPresentationReason.dailyLimit)
        val hourly = OverlayChromePolicy.chrome(BlockPresentationReason.hourlyLimit)
        val weekly = OverlayChromePolicy.chrome(BlockPresentationReason.weeklyLimit)

        assertEquals(OverlayAccentFamily.Period, daily.accent)
        assertEquals(OverlayAccentFamily.Period, hourly.accent)
        assertEquals(OverlayAccentFamily.Period, weekly.accent)
        assertEquals(OverlayChromeTitle.DailyLimit, daily.title)
        assertEquals(OverlayChromeTitle.HourlyLimit, hourly.title)
        assertEquals(OverlayChromeTitle.WeeklyLimit, weekly.title)
    }

    @Test
    fun otherReasons_useDefaultAccentAndExistingReasonLabel() {
        val others = listOf(
            BlockPresentationReason.scheduleBlock,
            BlockPresentationReason.outsideSchedule,
            BlockPresentationReason.focusMode,
            BlockPresentationReason.onBreak,
            BlockPresentationReason.extensionDenied,
            BlockPresentationReason.profilePin,
        )

        others.forEach { reason ->
            val chrome = OverlayChromePolicy.chrome(reason)
            assertEquals(reason.name, OverlayAccentFamily.Default, chrome.accent)
            assertEquals(reason.name, OverlayChromeTitle.ReasonLabel, chrome.title)
        }
    }
}
