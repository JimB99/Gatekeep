package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenGatePassPolicyTest {

    @Test
    fun skipsWithinGraceWindow() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            openGatePassedEpochMs = 5_000L,
        )
        assertTrue(
            OpenGatePassPolicy.shouldSkipOpenGate(session, nowEpochMs = 5_000L + 30_000L),
        )
    }

    @Test
    fun skipsForCurrentSessionAfterGraceExpires() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            openGatePassedEpochMs = 2_000L,
        )
        assertTrue(
            OpenGatePassPolicy.shouldSkipOpenGate(
                session,
                nowEpochMs = 2_000L + OpenGatePassPolicy.DEFAULT_GRACE_MS + 1,
            ),
        )
    }

    @Test
    fun doesNotSkipWhenNeverPassed() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
        )
        assertFalse(OpenGatePassPolicy.shouldSkipOpenGate(session, nowEpochMs = 2_000L))
    }

    @Test
    fun doesNotSkipAfterGraceAndNewSessionStart() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 100_000L,
            openGatePassedEpochMs = 2_000L,
        )
        assertFalse(
            OpenGatePassPolicy.shouldSkipOpenGate(
                session,
                nowEpochMs = 100_000L + OpenGatePassPolicy.DEFAULT_GRACE_MS + 1,
            ),
        )
    }

    @Test
    fun markOpenGatePassed_setsTimestamp() {
        val session = SessionState(packageName = "com.test", sessionStartEpochMs = 1L)
        val updated = OpenGatePassPolicy.markOpenGatePassed(session, 9_999L)
        assertEquals(9_999L, updated.openGatePassedEpochMs)
    }
}
