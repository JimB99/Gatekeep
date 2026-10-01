package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenGatePassPolicyTest {

    @Test
    fun skipsWhenPassedInCurrentSession() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            openGatePassedEpochMs = 5_000L,
        )
        assertTrue(
            OpenGatePassPolicy.shouldSkipOpenGate(session, nowEpochMs = 5_000L + 90_000L),
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
    fun doesNotSkipWhenPassedOnlyInPreviousSession() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 100_000L,
            openGatePassedEpochMs = 2_000L,
        )
        assertFalse(
            OpenGatePassPolicy.shouldSkipOpenGate(
                session,
                nowEpochMs = 100_000L + 1_000L,
            ),
        )
    }

    @Test
    fun doesNotSkipWhenPassedRecentlyButNewSessionStarted() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 50_000L,
            openGatePassedEpochMs = 40_000L,
        )
        assertFalse(
            OpenGatePassPolicy.shouldSkipOpenGate(
                session,
                nowEpochMs = 50_000L + 5_000L,
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
