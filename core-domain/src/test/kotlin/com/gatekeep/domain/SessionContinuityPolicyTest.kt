package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionContinuityPolicyTest {

    @Test
    fun shouldNotContinue_whenLastForegroundEndUnknown() {
        assertFalse(
            SessionContinuityPolicy.shouldContinueSession(
                lastForegroundEndEpochMs = null,
                nowEpochMs = 10_000L,
            ),
        )
    }

    @Test
    fun shouldContinue_whenGapUnderOneMinute() {
        val leftAt = 10_000L
        assertTrue(
            SessionContinuityPolicy.shouldContinueSession(
                lastForegroundEndEpochMs = leftAt,
                nowEpochMs = leftAt + 30_000L,
            ),
        )
    }

    @Test
    fun shouldNotContinue_whenGapAtLeastOneMinute() {
        val leftAt = 10_000L
        assertFalse(
            SessionContinuityPolicy.shouldContinueSession(
                lastForegroundEndEpochMs = leftAt,
                nowEpochMs = leftAt + SessionContinuityPolicy.RESUME_GRACE_MS,
            ),
        )
    }

    @Test
    fun resumeAfterGap_addsAwayTimeToExcludedMs() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            excludedMs = 5_000L,
            lastForegroundEndEpochMs = 10_000L,
        )
        val resumed = SessionContinuityPolicy.resumeAfterGap(session, nowEpochMs = 40_000L)
        assertEquals(35_000L, resumed.excludedMs)
        assertNull(resumed.lastForegroundEndEpochMs)
        assertEquals(1_000L, resumed.sessionStartEpochMs)
    }

    @Test
    fun activeAwayMs_isZeroWhenStillForeground() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            lastForegroundEndEpochMs = null,
        )
        assertEquals(0L, SessionContinuityPolicy.activeAwayMs(session, nowEpochMs = 40_000L))
    }

    @Test
    fun activeAwayMs_countsGapFromLastForegroundEnd() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            lastForegroundEndEpochMs = 10_000L,
        )
        assertEquals(30_000L, SessionContinuityPolicy.activeAwayMs(session, nowEpochMs = 40_000L))
    }

    @Test
    fun markForegroundEnded_preservesPassAndStart() {
        val session = SessionState(
            packageName = "com.test",
            sessionStartEpochMs = 1_000L,
            openGatePassedEpochMs = 2_000L,
        )
        val ended = SessionContinuityPolicy.markForegroundEnded(session, 9_000L)
        assertEquals(9_000L, ended.lastForegroundEndEpochMs)
        assertEquals(1_000L, ended.sessionStartEpochMs)
        assertEquals(2_000L, ended.openGatePassedEpochMs)
    }
}
