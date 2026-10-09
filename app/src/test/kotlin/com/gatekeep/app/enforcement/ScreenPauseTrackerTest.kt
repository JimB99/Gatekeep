package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPauseTrackerTest {

    @Test
    fun missedScreenOn_syncsTrackerWhenDisplayIsOn() {
        var now = 0L
        val tracker = ScreenPauseTracker { now }
        tracker.onScreenOff()
        assertFalse(tracker.screenOn)
        now = 5_000L
        tracker.syncFromHardware(displayOn = true)
        assertTrue(tracker.screenOn)
    }

    @Test
    fun displayOff_syncsTrackerOff() {
        val tracker = ScreenPauseTracker { 0L }
        assertTrue(tracker.screenOn)
        tracker.syncFromHardware(displayOn = false)
        assertFalse(tracker.screenOn)
    }
}
