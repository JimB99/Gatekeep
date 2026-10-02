package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenPauseTrackerTest {

    @Test
    fun stopwatch_doesNotAdvanceWhileScreenIsOff() {
        var now = 1_000L
        val tracker = ScreenPauseTracker { now }
        val stopwatch = tracker.createStopwatch()

        now += 1_000L
        assertEquals(1_000L, stopwatch.elapsedMs())

        tracker.onScreenOff()
        now += 3_000L
        assertEquals(1_000L, stopwatch.elapsedMs())

        assertEquals(3_000L, tracker.onScreenOn())
        now += 500L
        assertEquals(1_500L, stopwatch.elapsedMs())
    }

    @Test
    fun stopwatch_createdWhileOff_staysPaused() {
        var now = 5_000L
        val tracker = ScreenPauseTracker { now }
        tracker.onScreenOff()
        val stopwatch = tracker.createStopwatch()
        now += 2_000L
        assertEquals(0L, stopwatch.elapsedMs())
        tracker.onScreenOn()
        now += 400L
        assertEquals(400L, stopwatch.elapsedMs())
    }
}
