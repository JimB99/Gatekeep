package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenGateCompletionPolicyTest {

    @Test
    fun sessionAfterOpenGatePassed_clearsPendingWaitAndMarksPassed() {
        val now = 1_000_000L
        val session = SessionTracker.setPendingWait(
            SessionTracker.startFriction(
                SessionTracker.startSession("com.test.app", now - 60_000),
                now - 30_000,
            ),
            now + 45_000,
        )
        val updated = OpenGateCompletionPolicy.sessionAfterOpenGatePassed(session, now)
        assertNull(updated.pendingWaitUntilEpochMs)
        assertNull(updated.frictionStartedAtEpochMs)
        assertEquals(now, updated.openGatePassedEpochMs)
    }
}
