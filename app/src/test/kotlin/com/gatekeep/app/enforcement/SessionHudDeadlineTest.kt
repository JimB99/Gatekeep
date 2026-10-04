package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionHudDeadlineTest {

    @Test
    fun sessionDeadline_usesWallClockWhileForeground() {
        val now = 1_000_000L
        assertEquals(
            now + 45_000L,
            SessionHudDeadline.sessionDeadlineEpochMs(
                nowEpochMs = now,
                remainingSessionMs = 45_000L,
                lastForegroundEndEpochMs = null,
            ),
        )
    }

    @Test
    fun sessionDeadline_pausedWhileAway() {
        assertNull(
            SessionHudDeadline.sessionDeadlineEpochMs(
                nowEpochMs = 1_000_000L,
                remainingSessionMs = 45_000L,
                lastForegroundEndEpochMs = 970_000L,
            ),
        )
    }

    @Test
    fun displayedRemaining_frozenWhileDeadlinePaused() {
        assertEquals(
            45_000L,
            SessionHudDeadline.displayedSessionRemainingMs(
                nowEpochMs = 1_030_000L,
                sessionDeadlineEpochMs = null,
                pausedRemainingMs = 45_000L,
            ),
        )
    }

    @Test
    fun displayedRemaining_countsDownFromDeadlineWhileForeground() {
        val now = 1_000_000L
        assertEquals(
            20_000L,
            SessionHudDeadline.displayedSessionRemainingMs(
                nowEpochMs = now,
                sessionDeadlineEpochMs = now + 20_000L,
                pausedRemainingMs = 45_000L,
            ),
        )
    }
}
