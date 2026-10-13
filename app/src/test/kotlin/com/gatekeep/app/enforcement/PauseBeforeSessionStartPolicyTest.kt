package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseBeforeSessionStartPolicyTest {

    @Test
    fun holdsSessionStartWhileLocked() {
        assertTrue(PauseBeforeSessionStartPolicy.shouldHoldSessionStart(livePaused = true))
        assertFalse(PauseBeforeSessionStartPolicy.shouldHoldSessionStart(livePaused = false))
    }

    @Test
    fun stampsPrefsPauseWhenSessionHasNoMarker() {
        assertEquals(
            1_000L,
            PauseBeforeSessionStartPolicy.pauseAtToStamp(
                sessionPausedAtEpochMs = null,
                prefsPausedAtEpochMs = 1_000L,
                lastForegroundEndEpochMs = null,
            ),
        )
    }

    @Test
    fun doesNotStampWhenSessionAlreadyPaused() {
        assertNull(
            PauseBeforeSessionStartPolicy.pauseAtToStamp(
                sessionPausedAtEpochMs = 500L,
                prefsPausedAtEpochMs = 1_000L,
                lastForegroundEndEpochMs = null,
            ),
        )
    }

    @Test
    fun doesNotStampWhenLeaveAlreadyFrozeTheClock() {
        assertNull(
            PauseBeforeSessionStartPolicy.pauseAtToStamp(
                sessionPausedAtEpochMs = null,
                prefsPausedAtEpochMs = 1_000L,
                lastForegroundEndEpochMs = 900L,
            ),
        )
    }
}
