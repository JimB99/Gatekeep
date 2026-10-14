package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionContinuityGraceTest {

    @Test
    fun coerce_clampsBelowMinToFifteenSeconds() {
        assertEquals(15_000L, SessionContinuityGrace.coerce(0L))
    }

    @Test
    fun coerce_clampsAboveMaxToFifteenMinutes() {
        assertEquals(15 * 60_000L, SessionContinuityGrace.coerce(60 * 60_000L))
    }

    @Test
    fun defaultIsOneMinute() {
        assertEquals(60_000L, SessionContinuityGrace.DEFAULT_MS)
    }

    @Test
    fun coerce_keepsValueInsideRange() {
        assertEquals(90_000L, SessionContinuityGrace.coerce(90_000L))
    }
}
