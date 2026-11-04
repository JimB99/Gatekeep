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

    @Test
    fun skipsWhenLimitFrictionPaidThisSitting() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            limitFrictionPaidThisSitting = true,
        )
        assertTrue(OpenGatePassPolicy.shouldSkipOpenGate(session, nowEpochMs = 2_000L))
    }

    @Test
    fun markLimitFrictionPaid_alsoMarksOpenGatePassed() {
        val session = SessionState(packageName = "com.test", sessionStartEpochMs = 1_000L)
        val updated = SessionTracker.markLimitFrictionPaid(session)
        assertTrue(updated.limitFrictionPaidThisSitting)
        assertEquals(1_000L, updated.openGatePassedEpochMs)
        assertTrue(OpenGatePassPolicy.shouldSkipOpenGate(updated, nowEpochMs = 2_000L))
    }

    @Test
    fun staleInMemoryPass_doesNotSatisfyAfterFreshSessionStart() {
        val fresh = SessionState(packageName = "com.test", sessionStartEpochMs = 100_000L)
        assertFalse(
            OpenGatePassPolicy.openGateAlreadySatisfied(
                session = fresh,
                nowEpochMs = 100_500L,
                packageName = "com.test",
                inMemoryPassedPackage = "com.test",
            ),
        )
    }

    @Test
    fun inMemoryPass_matchesPersistedPassInSameSitting() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            openGatePassedEpochMs = 2_000L,
        )
        assertTrue(
            OpenGatePassPolicy.openGateAlreadySatisfied(
                session = session,
                nowEpochMs = 3_000L,
                packageName = "com.test",
                inMemoryPassedPackage = "com.test",
            ),
        )
    }

    @Test
    fun inMemoryPassBeforePersist_doesNotSatisfyUntilEpochSaved() {
        val session = SessionState(packageName = "com.test", sessionStartEpochMs = 1_000L)
        assertFalse(
            OpenGatePassPolicy.openGateAlreadySatisfied(
                session = session,
                nowEpochMs = 2_000L,
                packageName = "com.test",
                inMemoryPassedPackage = "com.test",
            ),
        )
        val saved = OpenGatePassPolicy.markOpenGatePassed(session, 2_000L)
        assertTrue(
            OpenGatePassPolicy.openGateAlreadySatisfied(
                session = saved,
                nowEpochMs = 2_500L,
                packageName = "com.test",
                inMemoryPassedPackage = "com.test",
            ),
        )
    }
}
