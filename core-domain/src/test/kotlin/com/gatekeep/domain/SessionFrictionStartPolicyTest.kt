package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionFrictionStartPolicyTest {

    private val pkg = "com.example.app"

    @Test
    fun startFrictionIfNeeded_setsWhenNone() {
        val session = SessionTracker.startSession(pkg, 1_000L)
        val updated = SessionFrictionStartPolicy.startFrictionIfNeeded(session, nowEpochMs = 1_500L)
        assertEquals(1_500L, updated?.frictionStartedAtEpochMs)
    }

    @Test
    fun startFrictionIfNeeded_skipsWhenAlreadyThisSitting() {
        val session = SessionTracker.startSession(pkg, 1_000L)
            .copy(frictionStartedAtEpochMs = 1_200L)
        assertNull(SessionFrictionStartPolicy.startFrictionIfNeeded(session, nowEpochMs = 9_000L))
    }

    @Test
    fun startFrictionIfNeeded_allowsAfterNewSitting() {
        val session = SessionState(
            packageName = pkg,
            sessionStartEpochMs = 10_000L,
            frictionStartedAtEpochMs = 5_000L,
        )
        val updated = SessionFrictionStartPolicy.startFrictionIfNeeded(session, nowEpochMs = 10_500L)
        assertEquals(10_500L, updated?.frictionStartedAtEpochMs)
    }
}
