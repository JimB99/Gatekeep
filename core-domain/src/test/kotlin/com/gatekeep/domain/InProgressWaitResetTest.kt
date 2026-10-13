package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue 04: an in-progress wait overlay resets on leave or lock.
 * Issue 05 verification: a passed on-open wait stays skipped across a <60s return.
 */
class InProgressWaitResetTest {

    private val pkg = "com.test"
    private val now = 1_000_000L
    private val waitMs = 60_000L

    private fun waitingSession(
        startedAt: Long = now,
        until: Long = now + waitMs,
        frictionAt: Long? = now,
        passedAt: Long? = null,
    ): SessionState {
        var session = SessionTracker.startSession(pkg, startedAt)
        if (frictionAt != null) {
            session = SessionTracker.startFriction(session, frictionAt)
        }
        session = SessionTracker.setPendingWait(session, until)
        if (passedAt != null) {
            session = OpenGatePassPolicy.markOpenGatePassed(session, passedAt)
        }
        return session
    }

    @Test
    fun leaveMidWait_discardsRemainingSeconds() {
        val leftAt = now + 10_000L
        val left = SessionContinuityPolicy.markForegroundEnded(
            waitingSession(),
            leftAt,
        )
        assertFalse(SessionTracker.hasPendingWait(left, leftAt))
        assertEquals(0L, SessionTracker.pendingWaitRemainingMs(left, leftAt))
        assertNull(left.pendingWaitUntilEpochMs)
        assertNull(left.frictionStartedAtEpochMs)
        assertEquals(leftAt, left.lastForegroundEndEpochMs)
    }

    @Test
    fun lockMidWait_discardsRemainingSeconds() {
        val pausedAt = now + 10_000L
        val paused = SessionTracker.pauseDeviceUse(waitingSession(), pausedAt)
        assertFalse(SessionTracker.hasPendingWait(paused, pausedAt))
        assertEquals(0L, SessionTracker.pendingWaitRemainingMs(paused, pausedAt))
        assertNull(paused.pendingWaitUntilEpochMs)
        assertEquals(pausedAt, paused.devicePausedAtEpochMs)
    }

    @Test
    fun unlockAfterLockMidWait_doesNotResumeLeftoverSeconds() {
        val pausedAt = now + 10_000L
        val unlockedAt = pausedAt + 15_000L
        val paused = SessionTracker.pauseDeviceUse(
            waitingSession(frictionAt = null),
            pausedAt,
        )
        val resumed = SessionTracker.resumeDeviceUse(paused, unlockedAt)
        assertFalse(SessionTracker.hasPendingWait(resumed, unlockedAt))
        assertEquals(0L, SessionTracker.pendingWaitRemainingMs(resumed, unlockedAt))
        assertNull(resumed.pendingWaitUntilEpochMs)
        assertNull(resumed.devicePausedAtEpochMs)
        assertEquals(15_000L, resumed.excludedMs)
    }

    @Test
    fun returnAfterLeaveMidWait_doesNotResumeFourLeftoverSeconds() {
        // logs/04-wait-counts-after-leave.txt: pending_wait_resume sec=4 after usage_paused
        val leftAt = now + 10_000L
        val returnedAt = leftAt + 9_663L
        val leftoverMs = 4_000L
        val left = SessionContinuityPolicy.markForegroundEnded(
            waitingSession(until = returnedAt + leftoverMs),
            leftAt,
        )
        val action = SessionStartDecision.decide(
            existingState = left,
            nowEpochMs = returnedAt,
            dayStartEpochMs = 0L,
            liveInMemorySession = true,
            pendingWait = SessionTracker.hasPendingWait(left, returnedAt),
        )
        assertEquals(SessionStartDecision.Action.ResumeAfterGap, action)
        val resumed = SessionContinuityPolicy.resumeAfterGap(left, returnedAt)
        assertFalse(SessionTracker.hasPendingWait(resumed, returnedAt))
        assertEquals(0L, SessionTracker.pendingWaitRemainingMs(resumed, returnedAt))
        assertFalse(OpenGatePassPolicy.shouldSkipOpenGate(resumed, returnedAt))
    }

    @Test
    fun passedOnOpen_returnAfter52s_skipsOnOpenWait() {
        // logs/05-open-overlay.txt: ResumeAfterGap gapMs=51990 then show_open_deterrent
        val sessionStart = now
        val passedAt = now + 16_000L
        val leftAt = passedAt + 92_000L
        val gapMs = 51_990L
        val returnedAt = leftAt + gapMs
        val existing = SessionState(
            packageName = pkg,
            sessionStartEpochMs = sessionStart,
            openGatePassedEpochMs = passedAt,
            lastForegroundEndEpochMs = leftAt,
        )
        val action = SessionStartDecision.decide(
            existingState = existing,
            nowEpochMs = returnedAt,
            dayStartEpochMs = 0L,
            liveInMemorySession = true,
        )
        assertEquals(SessionStartDecision.Action.ResumeAfterGap, action)
        val resumed = SessionContinuityPolicy.resumeAfterGap(existing, returnedAt)
        assertTrue(OpenGatePassPolicy.shouldSkipOpenGate(resumed, returnedAt))
    }

    @Test
    fun passedOnOpen_returnAfterOneMinute_requiresNewOnOpenWait() {
        val sessionStart = now
        val passedAt = now + 16_000L
        val leftAt = passedAt + 10_000L
        val returnedAt = leftAt + SessionContinuityPolicy.RESUME_GRACE_MS
        val existing = SessionState(
            packageName = pkg,
            sessionStartEpochMs = sessionStart,
            openGatePassedEpochMs = passedAt,
            lastForegroundEndEpochMs = leftAt,
        )
        val action = SessionStartDecision.decide(
            existingState = existing,
            nowEpochMs = returnedAt,
            dayStartEpochMs = 0L,
            liveInMemorySession = true,
        )
        assertEquals(SessionStartDecision.Action.StartFresh, action)
        val fresh = SessionTracker.startSession(pkg, returnedAt)
        assertFalse(OpenGatePassPolicy.shouldSkipOpenGate(fresh, returnedAt))
    }

    @Test
    fun leaveWithoutPendingWait_doesNotClearOpenGatePass() {
        val session = OpenGatePassPolicy.markOpenGatePassed(
            SessionTracker.startSession(pkg, now),
            now + 5_000L,
        )
        val left = SessionContinuityPolicy.markForegroundEnded(session, now + 20_000L)
        assertEquals(now + 5_000L, left.openGatePassedEpochMs)
        assertTrue(OpenGatePassPolicy.shouldSkipOpenGate(left, now + 20_000L))
    }
}
