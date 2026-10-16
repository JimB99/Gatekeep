package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.PeriodStarts
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeriodContinuePolicyTest {

    private val pkg = "com.test"
    private val now = 1_000_000L
    private val dailyCap = 60 * 60_000L
    private val sessionCap = 15 * 60_000L
    private val customRefill = 10 * 60_000L
    private val continueCustom = 12 * 60_000L
    private val periodStarts = PeriodStarts(dayStartMs = 0L, hourStartMs = 0L, weekStartMs = 0L)

    private fun limit() = AppLimit(
        profileId = 1,
        packageName = pkg,
        dailyLimitMs = dailyCap,
        sessionLimitMs = sessionCap,
        enabled = true,
    )

    private fun profile(
        action: OnLimitAction = OnLimitAction.deterrentWait,
        waitSeconds: Int = 60,
        breakMs: Long? = 5 * 60_000L,
        inherit: Boolean = true,
        continueMs: Long = LimitGrantDefaults.DEFAULT_PERIOD_CONTINUE_MS,
        sessionLimitMs: Long? = sessionCap,
        refillOverrideMs: Long? = customRefill,
    ) = Profile(
        id = 1,
        name = "Test",
        isActive = true,
        dailyLimitMs = dailyCap,
        sessionLimitMs = sessionLimitMs,
        onLimitAction = action,
        limitWaitDurationSeconds = waitSeconds,
        limitBreakDurationMs = breakMs,
        periodContinueInheritSessionCap = inherit,
        periodContinueDurationMs = continueMs,
        sessionRefillOverrideMs = refillOverrideMs,
    )

    private fun overCap(extraMs: Long = 1L) = UsageSnapshot(dailyMs = dailyCap + extraMs)

    private fun context(
        profile: Profile,
        usage: UsageSnapshot,
        session: com.gatekeep.domain.model.SessionState? = null,
        nowEpochMs: Long = now,
    ) = RuleEvaluationContext(
        nowEpochMs = nowEpochMs,
        packageName = pkg,
        profile = profile,
        limit = limit().copy(sessionLimitMs = profile.sessionLimitMs),
        isMonitored = true,
        usage = usage,
        sessionState = session,
        pauses = emptyList(),
        enforcementConfig = profile.enforcementConfig(),
        periodStarts = periodStarts,
    )

    private fun remainingDaily(result: RuleResult): Long {
        assertTrue(result is RuleResult.Allowed)
        return (result as RuleResult.Allowed).remainingDailyMs!!
    }

    @Test
    fun `wait grant then continue minutes of app-use then wait returns`() {
        val p = profile()
        val usageAtGrant = overCap()
        val blocked = RuleEngine.evaluate(context(p, usageAtGrant), openAlreadyPassed = true)
        assertTrue(blocked is RuleResult.Blocked)
        assertEquals(BlockReason.dailyLimit, (blocked as RuleResult.Blocked).reason)
        assertEquals(FrictionMethod.waitOneMin, blocked.sessionDeterrent)

        val continueMs = PeriodContinuePolicy.continueMs(p.enforcementConfig(), p.sessionLimitMs)
        assertEquals(sessionCap, continueMs)
        val granted = PeriodContinuePolicy.applyContinue(
            session = SessionTracker.startSession(pkg, now),
            usage = usageAtGrant,
            continueMs = continueMs,
            periodStarts = periodStarts,
        )
        assertTrue(granted.limitFrictionPaidThisSitting)
        assertEquals(0, granted.consecutiveExtensionCount)

        val allowed = RuleEngine.evaluate(
            context(p, usageAtGrant, granted),
            openAlreadyPassed = true,
        )
        assertEquals(continueMs, remainingDaily(allowed))

        val usedContinue = UsageSnapshot(dailyMs = usageAtGrant.dailyMs + continueMs)
        val again = RuleEngine.evaluate(
            context(p, usedContinue, granted),
            openAlreadyPassed = true,
        )
        assertTrue(again is RuleResult.Blocked)
        assertEquals(FrictionMethod.waitOneMin, (again as RuleResult.Blocked).sessionDeterrent)
    }

    @Test
    fun `continue grant clears an in-progress wait deadline`() {
        val waiting = SessionTracker.startSession(pkg, now).copy(pendingWaitUntilEpochMs = now + 30_000L)
        val granted = PeriodContinuePolicy.applyContinue(
            session = waiting,
            usage = overCap(),
            continueMs = continueCustom,
            periodStarts = periodStarts,
        )
        assertFalse(SessionTracker.hasPendingWait(granted, now + 1_000L))
        assertNull(granted.pendingWaitUntilEpochMs)
    }

    @Test
    fun `continue inherit uses the session cap not a custom session-refill override`() {
        val p = profile(inherit = true, continueMs = continueCustom, refillOverrideMs = customRefill)
        val continueMs = PeriodContinuePolicy.continueMs(p.enforcementConfig(), p.sessionLimitMs)
        assertEquals(sessionCap, continueMs)
        assertNotEquals(customRefill, continueMs)
        assertNotEquals(continueCustom, continueMs)
    }

    @Test
    fun `leave during period wait discards leftover seconds so the next wait is full`() {
        val waiting = SessionTracker.setPendingWait(
            SessionTracker.startFriction(SessionTracker.startSession(pkg, now), now),
            now + 40_000L,
        )
        val left = SessionContinuityPolicy.markForegroundEnded(waiting, now + 10_000L)
        assertFalse(SessionTracker.hasPendingWait(left, now + 10_000L))
        assertNull(left.pendingWaitUntilEpochMs)

        val result = RuleEngine.evaluate(
            context(profile(waitSeconds = 60), overCap(), left, nowEpochMs = now + 10_000L),
            openAlreadyPassed = true,
        )
        assertTrue(result is RuleResult.Blocked)
        assertEquals(FrictionMethod.waitOneMin, (result as RuleResult.Blocked).sessionDeterrent)
        assertEquals(60, profile(waitSeconds = 60).limitWaitDurationSeconds)
    }

    @Test
    fun `leave during period break keeps the remaining countdown`() {
        val breakUntil = now + 30_000L
        val onBreak = SessionTracker.applyBreak(SessionTracker.startSession(pkg, now), breakUntil)
        val p = profile(action = OnLimitAction.mandatoryBreak)
        val whileAway = RuleEngine.evaluate(
            context(p, overCap(), onBreak, nowEpochMs = now + 10_000L),
            openAlreadyPassed = true,
        )
        assertTrue(whileAway is RuleResult.Blocked)
        assertEquals(breakUntil, (whileAway as RuleResult.Blocked).breakUntilEpochMs)
        assertFalse(whileAway.bypassAllowed)
    }

    @Test
    fun `period break end grants continue once and does not re-enter with a past deadline`() {
        val p = profile(action = OnLimitAction.mandatoryBreak, breakMs = 5 * 60_000L)
        val pastDeadline = now - 1_000L
        val expired = SessionTracker.applyBreak(SessionTracker.startSession(pkg, now), pastDeadline)
        val continueMs = PeriodContinuePolicy.continueMs(p.enforcementConfig(), p.sessionLimitMs)
        val usage = overCap()

        val completed = PeriodContinuePolicy.completeExpiredPeriodBreak(
            session = expired,
            usage = usage,
            continueMs = continueMs,
            periodStarts = periodStarts,
            nowEpochMs = now,
        )
        assertNull(completed.breakUntilEpochMs)
        assertTrue(completed.limitFrictionPaidThisSitting)

        val allowed = RuleEngine.evaluate(context(p, usage, completed), openAlreadyPassed = true)
        assertEquals(continueMs, remainingDaily(allowed))

        val looped = RuleEngine.evaluate(context(p, usage, completed), openAlreadyPassed = true)
        assertTrue(looped is RuleResult.Allowed)
        assertNull((looped as? RuleResult.Blocked)?.breakUntilEpochMs)

        val used = UsageSnapshot(dailyMs = usage.dailyMs + continueMs)
        val nextBreak = RuleEngine.evaluate(context(p, used, completed), openAlreadyPassed = true)
        assertTrue(nextBreak is RuleResult.Blocked)
        val nextUntil = (nextBreak as RuleResult.Blocked).breakUntilEpochMs
        assertNotNull(nextUntil)
        assertEquals(now + 5 * 60_000L, nextUntil)
        assertNotEquals(pastDeadline, nextUntil)
    }

    @Test
    fun `period extend extras do not burn on leave and do burn on foreground usage`() {
        val p = profile(action = OnLimitAction.limitWithExtensions)
        val usageAtGrant = overCap()
        val extra = 5 * 60_000L
        val granted = PeriodContinuePolicy.applyExtendExtras(
            session = SessionTracker.startSession(pkg, now),
            usage = usageAtGrant,
            extraMs = extra,
            periodStarts = periodStarts,
        )
        val stillAway = RuleEngine.evaluate(
            context(p, usageAtGrant, granted),
            openAlreadyPassed = true,
        )
        assertEquals(extra, remainingDaily(stillAway))
        assertFalse(granted.limitFrictionPaidThisSitting)

        val afterUse = UsageSnapshot(dailyMs = usageAtGrant.dailyMs + 3 * 60_000L)
        val mid = RuleEngine.evaluate(context(p, afterUse, granted), openAlreadyPassed = true)
        assertEquals(2 * 60_000L, remainingDaily(mid))

        val exhausted = UsageSnapshot(dailyMs = usageAtGrant.dailyMs + extra)
        val blocked = RuleEngine.evaluate(context(p, exhausted, granted), openAlreadyPassed = true)
        assertTrue(blocked is RuleResult.Blocked)
        assertEquals(BlockReason.dailyLimit, (blocked as RuleResult.Blocked).reason)
        assertTrue(blocked.bypassAllowed)
    }

    @Test
    fun `period extra exhausted stays blocked without a continue path`() {
        val p = profile(action = OnLimitAction.limitWithExtensions)
        val result = RuleEngine.evaluate(context(p, overCap()), openAlreadyPassed = true)
        assertTrue(result is RuleResult.Blocked)
        assertTrue((result as RuleResult.Blocked).bypassAllowed)
        assertNull(result.sessionDeterrent)
    }

    @Test
    fun `hourly extra exhausted does not wait for the day reset`() {
        val hourly = AppLimit(
            profileId = 1,
            packageName = pkg,
            dailyLimitMs = dailyCap,
            hourlyLimitMs = 20 * 60_000L,
            enabled = true,
        )
        val p = profile(action = OnLimitAction.limitWithExtensions)
        val result = RuleEngine.evaluate(
            RuleEvaluationContext(
                nowEpochMs = now,
                packageName = pkg,
                profile = p,
                limit = hourly,
                isMonitored = true,
                usage = UsageSnapshot(dailyMs = 10 * 60_000L, hourlyMs = 20 * 60_000L + 1),
                sessionState = null,
                pauses = emptyList(),
                enforcementConfig = p.enforcementConfig(),
                periodStarts = periodStarts,
            ),
            openAlreadyPassed = true,
        )
        assertTrue(result is RuleResult.Blocked)
        assertEquals(BlockReason.hourlyLimit, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun `wait length 0 silently continues without an overlay`() {
        val p = profile(waitSeconds = 0)
        val applied = PeriodContinuePolicy.applySilentContinueIfNeeded(
            session = SessionTracker.startSession(pkg, now),
            limit = limit(),
            usage = overCap(),
            config = p.enforcementConfig(),
            periodStarts = periodStarts,
        )
        assertNotNull(applied)
        val allowed = RuleEngine.evaluate(context(p, overCap(), applied), openAlreadyPassed = true)
        assertEquals(sessionCap, remainingDaily(allowed))
        assertFalse(applied!!.limitFrictionPaidThisSitting)
    }

    @Test
    fun `break length 0 silently continues the continue amount`() {
        val p = profile(
            action = OnLimitAction.mandatoryBreak,
            breakMs = 0L,
            inherit = false,
            continueMs = continueCustom,
        )
        val applied = PeriodContinuePolicy.applySilentContinueIfNeeded(
            session = SessionTracker.startSession(pkg, now),
            limit = limit(),
            usage = overCap(),
            config = p.enforcementConfig(),
            periodStarts = periodStarts,
        )
        assertNotNull(applied)
        assertEquals(continueCustom, applied!!.periodAppUse.remainingMs(overCap(), periodStarts, limit()))
    }

    @Test
    fun `non-zero wait does not silent-continue`() {
        assertNull(
            PeriodContinuePolicy.applySilentContinueIfNeeded(
                session = SessionTracker.startSession(pkg, now),
                limit = limit(),
                usage = overCap(),
                config = profile(waitSeconds = 60).enforcementConfig(),
                periodStarts = periodStarts,
            ),
        )
    }

    @Test
    fun `math never silent-continues even if wait length is 0`() {
        assertFalse(
            PeriodContinuePolicy.shouldSilentAutoContinue(
                profile(action = OnLimitAction.deterrentMath, waitSeconds = 0).enforcementConfig(),
            ),
        )
    }

    @Test
    fun `silent continue repeats when the chunk is used`() {
        val config = profile(waitSeconds = 0).enforcementConfig()
        val first = PeriodContinuePolicy.applySilentContinueIfNeeded(
            session = SessionTracker.startSession(pkg, now),
            limit = limit(),
            usage = overCap(),
            config = config,
            periodStarts = periodStarts,
        )!!
        val used = UsageSnapshot(dailyMs = overCap().dailyMs + sessionCap)
        val second = PeriodContinuePolicy.applySilentContinueIfNeeded(
            session = first,
            limit = limit(),
            usage = used,
            config = config,
            periodStarts = periodStarts,
        )
        assertNotNull(second)
        assertEquals(sessionCap, second!!.periodAppUse.remainingMs(used, periodStarts, limit()))
    }

    @Test
    fun `continue grant does not increment consecutive extension quota`() {
        val used = SessionTracker.incrementConsecutiveExtensions(SessionTracker.startSession(pkg, now))
        val granted = PeriodContinuePolicy.applyContinue(
            session = used,
            usage = overCap(),
            continueMs = sessionCap,
            periodStarts = periodStarts,
        )
        assertEquals(1, granted.consecutiveExtensionCount)
    }

    @Test
    fun `shared pool remaining ticks from pooled usage not a single package`() {
        val usageA = UsageSnapshot(dailyMs = dailyCap + 1)
        val granted = PeriodContinuePolicy.applyContinue(
            session = SessionTracker.startSession(pkg, now),
            usage = usageA,
            continueMs = sessionCap,
            periodStarts = periodStarts,
        )
        val pooled = UsageSnapshot(dailyMs = usageA.dailyMs + 4 * 60_000L)
        assertEquals(
            sessionCap - 4 * 60_000L,
            granted.periodAppUse.remainingMs(pooled, periodStarts, limit()),
        )
    }
}
