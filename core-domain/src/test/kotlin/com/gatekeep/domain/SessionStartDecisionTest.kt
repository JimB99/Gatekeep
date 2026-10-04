package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionStartDecisionTest {

    private val pkg = "com.test"
    private val dayStart = 4 * 60 * 60_000L
    private val now = dayStart + 5 * 60 * 60_000L

    private fun session(
        start: Long = dayStart + 1_000L,
        lastEnd: Long? = null,
        excluded: Long = 0L,
    ) = SessionState(
        packageName = pkg,
        sessionStartEpochMs = start,
        excludedMs = excluded,
        lastForegroundEndEpochMs = lastEnd,
    )

    private fun decide(
        existing: SessionState?,
        live: Boolean,
        pendingWait: Boolean = false,
        onBreak: Boolean = false,
        nowEpochMs: Long = now,
        dayStartEpochMs: Long = dayStart,
    ) = SessionStartDecision.decide(
        existingState = existing,
        nowEpochMs = nowEpochMs,
        dayStartEpochMs = dayStartEpochMs,
        liveInMemorySession = live,
        pendingWait = pendingWait,
        onBreak = onBreak,
    )

    @Test
    fun nullExisting_startsFresh() {
        assertEquals(SessionStartDecision.Action.StartFresh, decide(null, live = false))
        assertEquals(SessionStartDecision.Action.StartFresh, decide(null, live = true))
    }

    @Test
    fun pendingWait_continues() {
        assertEquals(
            SessionStartDecision.Action.Continue,
            decide(session(), live = false, pendingWait = true),
        )
    }

    @Test
    fun onBreak_continues() {
        assertEquals(
            SessionStartDecision.Action.Continue,
            decide(session(), live = false, onBreak = true),
        )
    }

    @Test
    fun liveInMemory_nullLastEnd_continues() {
        assertEquals(
            SessionStartDecision.Action.Continue,
            decide(session(lastEnd = null), live = true),
        )
    }

    @Test
    fun liveInMemory_acrossDayStart_continues() {
        val overnight = session(start = dayStart - 10 * 60_000L, lastEnd = null)
        assertEquals(
            SessionStartDecision.Action.Continue,
            decide(overnight, live = true),
        )
    }

    @Test
    fun liveInMemory_gapUnderOneMinute_resumes() {
        val leftAt = now - 30_000L
        assertEquals(
            SessionStartDecision.Action.ResumeAfterGap,
            decide(session(lastEnd = leftAt), live = true),
        )
    }

    @Test
    fun liveInMemory_gapAtLeastOneMinute_startsFresh() {
        val leftAt = now - SessionContinuityPolicy.RESUME_GRACE_MS
        assertEquals(
            SessionStartDecision.Action.StartFresh,
            decide(session(lastEnd = leftAt), live = true),
        )
    }

    @Test
    fun processDeath_nullLastEnd_startsFresh() {
        val yesterday = session(start = dayStart - 12 * 60 * 60_000L, lastEnd = null)
        assertEquals(
            SessionStartDecision.Action.StartFresh,
            decide(yesterday, live = false),
        )
    }

    @Test
    fun processDeath_sessionBeforeDayStart_startsFresh() {
        val leftRecently = session(
            start = dayStart - 2 * 60_000L,
            lastEnd = now - 10_000L,
        )
        assertEquals(
            SessionStartDecision.Action.StartFresh,
            decide(leftRecently, live = false),
        )
    }

    @Test
    fun notLive_gapUnderOneMinute_sameDay_resumes() {
        val leftAt = now - 30_000L
        assertEquals(
            SessionStartDecision.Action.ResumeAfterGap,
            decide(session(lastEnd = leftAt), live = false),
        )
    }

    @Test
    fun notLive_gapAtLeastOneMinute_startsFresh() {
        val leftAt = now - SessionContinuityPolicy.RESUME_GRACE_MS
        assertEquals(
            SessionStartDecision.Action.StartFresh,
            decide(session(lastEnd = leftAt), live = false),
        )
    }
}
