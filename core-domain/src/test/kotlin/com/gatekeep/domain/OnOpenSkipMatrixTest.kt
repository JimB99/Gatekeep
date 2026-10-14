package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.PeriodStarts
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SessionState
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Spec: on-open on a new sitting unless the usage winner is hard block or break.
 * Wait/math/extend/notify may run as two gates. Paid wait/math/break skips on-open
 * for this sitting even after refill/continue.
 */
class OnOpenSkipMatrixTest {

    private val pkg = "com.test.app"
    private val now = 1_000_000L
    private val dailyCap = 60 * 60_000L
    private val sessionCap = 15 * 60_000L

    private val profileBase = Profile(
        id = 1,
        name = "Test",
        isActive = true,
        dailyLimitMs = dailyCap,
        sessionLimitMs = sessionCap,
        limitBreakDurationMs = 5 * 60_000L,
        breakDurationMs = 5 * 60_000L,
    )

    private fun limit() = AppLimit(
        profileId = 1,
        packageName = pkg,
        dailyLimitMs = dailyCap,
        sessionLimitMs = sessionCap,
        breakDurationMs = 5 * 60_000L,
        enabled = true,
    )

    private fun context(
        profile: Profile,
        usage: UsageSnapshot = UsageSnapshot(),
        sessionState: SessionState? = null,
        nowEpochMs: Long = now,
    ) = RuleEvaluationContext(
        nowEpochMs = nowEpochMs,
        packageName = pkg,
        profile = profile,
        limit = limit(),
        isMonitored = true,
        usage = usage,
        sessionState = sessionState,
        pauses = emptyList(),
        enforcementConfig = profile.enforcementConfig(),
    )

    @Test
    fun newSitting_dailyHardBlock_skipsOnOpen() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.hardBlock,
        )
        val result = RuleEngine.evaluate(
            context(profile, usage = UsageSnapshot(dailyMs = dailyCap + 1)),
        )
        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.dailyLimit, (result as RuleResult.Blocked).reason)
        assertFalse(result.bypassAllowed)
    }

    @Test
    fun newSitting_periodWait_onOpenWait_showsOnOpenFirst() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.deterrentWait,
        )
        val result = RuleEngine.evaluate(
            context(profile, usage = UsageSnapshot(dailyMs = dailyCap + 1)),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
        assertEquals(FrictionMethod.waitOneMin, (result as RuleResult.OpenDeterrent).method)
    }

    @Test
    fun newSitting_periodMath_onOpenWait_showsOnOpenFirst() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.deterrentMath,
        )
        val result = RuleEngine.evaluate(
            context(profile, usage = UsageSnapshot(dailyMs = dailyCap + 1)),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun newSitting_periodExtend_onOpenWait_showsOnOpenFirst() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.limitWithExtensions,
        )
        val result = RuleEngine.evaluate(
            context(profile, usage = UsageSnapshot(dailyMs = dailyCap + 1)),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun afterPeriodWaitPaid_returnInsideGrace_skipsOnOpen() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.deterrentWait,
        )
        val sitting = SessionTracker.markLimitFrictionPaid(
            SessionTracker.startSession(pkg, now - 5_000L),
        )
        val left = SessionContinuityPolicy.markForegroundEnded(sitting, now)
        val resumed = SessionContinuityPolicy.resumeAfterGap(left, now + 20_000L)
        assertTrue(OpenGatePassPolicy.shouldSkipOpenGate(resumed, now + 20_000L))

        val result = RuleEngine.evaluate(
            context(
                profile,
                usage = UsageSnapshot(dailyMs = dailyCap + 1),
                sessionState = resumed,
                nowEpochMs = now + 20_000L,
            ),
        )
        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.dailyLimit, (result as RuleResult.Blocked).reason)
        assertEquals(FrictionMethod.waitOneMin, (result as RuleResult.Blocked).sessionDeterrent)
    }

    @Test
    fun afterSessionWaitPaid_leaveAtLeastGrace_showsOnOpenAgain() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.deterrentWait,
            onSessionLimitAction = OnSessionLimitAction.deterrentWait,
        )
        val paid = SessionTracker.markLimitFrictionPaid(
            SessionTracker.startSession(pkg, now - 5_000L),
        )
        assertTrue(OpenGatePassPolicy.shouldSkipOpenGate(paid, now))

        val returnedAt = now + SessionContinuityPolicy.RESUME_GRACE_MS
        val fresh = SessionTracker.startSession(pkg, returnedAt)
        assertFalse(OpenGatePassPolicy.shouldSkipOpenGate(fresh, returnedAt))

        val result = RuleEngine.evaluate(
            context(
                profile,
                usage = UsageSnapshot(dailyMs = dailyCap + 1),
                sessionState = fresh,
                nowEpochMs = returnedAt,
            ),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun sessionHardBlock_andPeriodWait_sessionWins() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.deterrentWait,
            onSessionLimitAction = OnSessionLimitAction.hardBlock,
        )
        val session = SessionTracker.startSession(pkg, now - sessionCap - 1_000L)
        val result = RuleEngine.evaluate(
            context(
                profile,
                usage = UsageSnapshot(dailyMs = dailyCap + 1),
                sessionState = session,
            ),
        )
        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.sessionLimit, (result as RuleResult.Blocked).reason)
        assertFalse((result as RuleResult.Blocked).bypassAllowed)
    }

    @Test
    fun afterLeaveFromSessionHardBlock_onOpenThenPeriodWait() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.deterrentWait,
            onSessionLimitAction = OnSessionLimitAction.hardBlock,
        )
        val returnedAt = now + SessionContinuityPolicy.RESUME_GRACE_MS
        val fresh = SessionTracker.startSession(pkg, returnedAt)
        val result = RuleEngine.evaluate(
            context(
                profile,
                usage = UsageSnapshot(dailyMs = dailyCap + 1),
                sessionState = fresh,
                nowEpochMs = returnedAt,
            ),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
        val afterOpen = RuleEngine.evaluate(
            context(
                profile,
                usage = UsageSnapshot(dailyMs = dailyCap + 1),
                sessionState = fresh,
                nowEpochMs = returnedAt,
            ),
            openAlreadyPassed = true,
        )
        assertInstanceOf(RuleResult.Blocked::class.java, afterOpen)
        assertEquals(BlockReason.dailyLimit, (afterOpen as RuleResult.Blocked).reason)
        assertEquals(FrictionMethod.waitOneMin, afterOpen.sessionDeterrent)
    }

    @Test
    fun newSitting_silentPeriodWait0_stillShowsOnOpen() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.deterrentWait,
            limitWaitDurationSeconds = 0,
        )
        val granted = PeriodContinuePolicy.applySilentContinueIfNeeded(
            session = SessionTracker.startSession(pkg, now),
            limit = limit(),
            usage = UsageSnapshot(dailyMs = dailyCap + 1),
            config = profile.enforcementConfig(),
            periodStarts = PeriodStarts(),
        )
        assertNotNull(granted)
        assertFalse(granted!!.limitFrictionPaidThisSitting)
        assertFalse(OpenGatePassPolicy.shouldSkipOpenGate(granted, now))
        val result = RuleEngine.evaluate(
            context(profile, usage = UsageSnapshot(dailyMs = dailyCap + 1), sessionState = granted),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun afterBreakPaid_skipsOnOpenEvenOnFreshSessionClock() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.notifyOnly,
            onSessionLimitAction = OnSessionLimitAction.mandatoryBreak,
        )
        val expired = SessionTracker.startSession(pkg, now - sessionCap)
            .copy(breakUntilEpochMs = now - 1L)
        val completed = SessionTracker.completeExpiredBreak(expired, now)
        assertTrue(completed.limitFrictionPaidThisSitting)
        val result = RuleEngine.evaluate(context(profile, sessionState = completed, nowEpochMs = now))
        assertFalse(result is RuleResult.OpenDeterrent)
    }
}
