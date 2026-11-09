package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenGateFrictionInProgressPolicyTest {

    private val pkg = "com.example.app"

    @Test
    fun frictionThisSitting_withOpenGatePosted_returnsTrue() {
        val session = SessionTracker.startSession(pkg, 1_000L)
            .copy(frictionStartedAtEpochMs = 1_500L)
        assertTrue(
            OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
                session,
                nowEpochMs = 2_000L,
                openGateBlockActive = true,
            ),
        )
    }

    @Test
    fun limitFrictionOnly_withoutOpenGatePosted_returnsFalse() {
        val session = SessionTracker.startSession(pkg, 1_000L)
            .copy(frictionStartedAtEpochMs = 1_500L)
        assertFalse(
            OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
                session,
                nowEpochMs = 2_000L,
                openGateBlockActive = false,
            ),
        )
    }

    @Test
    fun openGatePosted_beforeFrictionPersisted_returnsTrue() {
        val session = SessionTracker.startSession(pkg, 1_000L)
        assertTrue(
            OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
                session,
                nowEpochMs = 2_000L,
                openGateBlockActive = true,
            ),
        )
    }

    @Test
    fun openGatePassedThisSitting_returnsFalse() {
        val session = OpenGatePassPolicy.markOpenGatePassed(
            SessionTracker.startSession(pkg, 1_000L).copy(frictionStartedAtEpochMs = 1_500L),
            nowEpochMs = 1_800L,
        )
        assertFalse(
            OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
                session,
                nowEpochMs = 2_000L,
                openGateBlockActive = true,
            ),
        )
    }

    @Test
    fun staleFrictionFromPriorSitting_returnsFalse() {
        val session = SessionTracker.startSession(pkg, 10_000L)
            .copy(frictionStartedAtEpochMs = 5_000L)
        assertFalse(
            OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
                session,
                nowEpochMs = 11_000L,
                openGateBlockActive = true,
            ),
        )
    }

    @Test
    fun pendingOpenGateWait_returnsTrueWithoutPostedFlag() {
        val session = SessionState(
            packageName = pkg,
            sessionStartEpochMs = 1_000L,
            pendingWaitUntilEpochMs = 5_000L,
        )
        assertTrue(
            OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
                session,
                nowEpochMs = 2_000L,
                openGateBlockActive = false,
            ),
        )
    }

    @Test
    fun expiredPendingWait_noOpenGatePosted_returnsFalse() {
        val session = SessionState(
            packageName = pkg,
            sessionStartEpochMs = 1_000L,
            pendingWaitUntilEpochMs = 1_500L,
        )
        assertFalse(
            OpenGateFrictionInProgressPolicy.shouldSuppressNewOpenGateDeterrent(
                session,
                nowEpochMs = 2_000L,
                openGateBlockActive = false,
            ),
        )
    }
}
