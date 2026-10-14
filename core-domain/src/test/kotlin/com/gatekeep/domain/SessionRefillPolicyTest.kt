package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.ProfileEnforcementConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionRefillPolicyTest {

    private val pkg = "com.test"
    private val cap = 15 * 60_000L
    private val customRefill = 10 * 60_000L
    private val now = 1_000_000L

    private fun limit(breakMs: Long? = null) = AppLimit(
        profileId = 1,
        packageName = pkg,
        sessionLimitMs = cap,
        breakDurationMs = breakMs,
    )

    private fun config(
        action: OnSessionLimitAction = OnSessionLimitAction.deterrentWait,
        waitSeconds: Int = 60,
        breakMs: Long? = 5 * 60_000L,
        refillOverrideMs: Long? = null,
    ) = ProfileEnforcementConfig(
        onSessionLimitAction = action,
        sessionWaitDurationSeconds = waitSeconds,
        sessionBreakDurationMs = breakMs,
        sessionRefillOverrideMs = refillOverrideMs,
    )

    private fun atCap() = SessionTracker.startSession(pkg, now - cap)

    private fun remaining(session: com.gatekeep.domain.model.SessionState): Long {
        val result = SessionTracker.evaluateSession(limit(), session, now)
        assertTrue(result is SessionTracker.SessionCheckResult.Allowed)
        return (result as SessionTracker.SessionCheckResult.Allowed).remainingSessionMs!!
    }

    @Test
    fun `wait grant with no override allows a full session cap of app-use`() {
        val refilled = SessionTracker.applyAppUseRefill(atCap(), cap, cap, now)
        assertEquals(cap, remaining(refilled))
        assertTrue(refilled.limitFrictionPaidThisSitting)
        assertEquals(0, refilled.consecutiveExtensionCount)
    }

    @Test
    fun `custom refill is smaller than a fresh sitting after leave`() {
        val refilled = SessionTracker.applyAppUseRefill(
            atCap(),
            sessionLimitMs = cap,
            refillMs = customRefill,
            nowEpochMs = now,
        )
        assertEquals(customRefill, remaining(refilled))

        val afterLeave = SessionTracker.startSession(pkg, now + SessionContinuityPolicy.RESUME_GRACE_MS)
        assertEquals(cap, remaining(afterLeave))
        assertFalse(afterLeave.limitFrictionPaidThisSitting)
    }

    @Test
    fun `non-zero break end grants a full session cap not the refill override`() {
        val expired = atCap().copy(breakUntilEpochMs = now - 1_000L)
        val completed = SessionTracker.completeExpiredBreak(expired, now)
        assertEquals(cap, remaining(completed))
        assertTrue(completed.limitFrictionPaidThisSitting)
        assertNotEquals(customRefill, remaining(completed))
    }

    @Test
    fun `wait refill does not increment consecutive extension quota`() {
        val used = SessionTracker.incrementConsecutiveExtensions(atCap())
        val refilled = SessionTracker.applyAppUseRefill(used, cap, cap, now)
        assertEquals(1, refilled.consecutiveExtensionCount)
    }

    @Test
    fun `leave under the grace does not burn refill remaining`() {
        val refilled = SessionTracker.applyAppUseRefill(atCap(), cap, customRefill, now)
        val left = SessionContinuityPolicy.markForegroundEnded(refilled, now)
        val resumed = SessionContinuityPolicy.resumeAfterGap(left, now + 30_000L)
        val result = SessionTracker.evaluateSession(limit(), resumed, now + 30_000L)
        assertTrue(result is SessionTracker.SessionCheckResult.Allowed)
        assertEquals(
            customRefill,
            (result as SessionTracker.SessionCheckResult.Allowed).remainingSessionMs,
        )
    }

    @Test
    fun `overshoot past the cap still yields the full refill remaining`() {
        val overshot = SessionTracker.startSession(pkg, now - cap - 2_000L)
        val refilled = SessionTracker.applyAppUseRefill(overshot, cap, customRefill, now)
        assertEquals(customRefill, remaining(refilled))
    }

    @Test
    fun `wait length 0 silently refills without needing an overlay`() {
        val applied = SessionRefillPolicy.applySilentRefillIfNeeded(
            session = atCap(),
            limit = limit(),
            config = config(waitSeconds = 0),
            nowEpochMs = now,
        )
        assertNotNull(applied)
        assertEquals(cap, remaining(applied!!))
        assertFalse(applied.limitFrictionPaidThisSitting)
    }

    @Test
    fun `break length 0 silently refills the refill amount`() {
        val applied = SessionRefillPolicy.applySilentRefillIfNeeded(
            session = atCap(),
            limit = limit(breakMs = 0L),
            config = config(
                action = OnSessionLimitAction.mandatoryBreak,
                breakMs = null,
                refillOverrideMs = customRefill,
            ),
            nowEpochMs = now,
        )
        assertNotNull(applied)
        assertEquals(customRefill, remaining(applied!!))
    }

    @Test
    fun `non-zero wait does not silent-refill`() {
        assertNull(
            SessionRefillPolicy.applySilentRefillIfNeeded(
                session = atCap(),
                limit = limit(),
                config = config(waitSeconds = 60),
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun `non-zero break does not silent-refill while the countdown is running`() {
        val onBreak = atCap().copy(breakUntilEpochMs = now + 60_000L)
        assertNull(
            SessionRefillPolicy.applySilentRefillIfNeeded(
                session = onBreak,
                limit = limit(breakMs = 5 * 60_000L),
                config = config(action = OnSessionLimitAction.mandatoryBreak, breakMs = 5 * 60_000L),
                nowEpochMs = now,
            ),
        )
        assertTrue(SessionTracker.isOnBreak(onBreak, now))
    }

    @Test
    fun `math never silent-refills even if wait length is 0`() {
        assertFalse(
            SessionRefillPolicy.shouldSilentAutoRefill(
                config(action = OnSessionLimitAction.deterrentMath, waitSeconds = 0),
            ),
        )
    }

    @Test
    fun `silent refill repeats when the sitting hits the cap again`() {
        val first = SessionRefillPolicy.applySilentRefillIfNeeded(
            session = atCap(),
            limit = limit(),
            config = config(waitSeconds = 0, refillOverrideMs = customRefill),
            nowEpochMs = now,
        )!!
        val usedAgain = first.copy(sessionStartEpochMs = now - cap - customRefill)
        val second = SessionRefillPolicy.applySilentRefillIfNeeded(
            session = usedAgain,
            limit = limit(),
            config = config(waitSeconds = 0, refillOverrideMs = customRefill),
            nowEpochMs = now,
        )
        assertNotNull(second)
        assertEquals(customRefill, remaining(second!!))
    }

    @Test
    fun `upgrade wait profiles refill the session cap not five minutes`() {
        val refillMs = SessionRefillPolicy.refillMs(cap, LimitGrantDefaults.migrateSessionRefillOverrideMs())
        assertEquals(cap, refillMs)
        assertNotEquals(5 * 60_000L, refillMs)
    }
}
