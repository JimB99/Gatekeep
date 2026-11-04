package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SessionState
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Log repro (unlock → StartFresh → evaluate without `show_open_deterrent`):
 * stale in-memory open-gate pass while Room started a fresh sitting.
 *
 * Expected trace after fix: `session_start action=StartFresh` then `show_open_deterrent method=math`.
 * See `OpenGatePassPolicyTest.staleInMemoryPass_*` and G-15 in docs/test-scenarios.md.
 */
class OpenGateStartFreshRegressionTest {

    private val pkg = "app.revanced.android.youtube"
    private val now = 1_000_000L

    private val profile = Profile(
        id = 1,
        name = "Test",
        isActive = true,
        onOpenAction = OnOpenAction.deterrentMath,
    )

    private val limit = AppLimit(
        profileId = 1,
        packageName = pkg,
        sessionLimitMs = 60 * 60_000L,
        enabled = true,
    )

    @Test
    fun startFreshSession_staleInMemoryPass_doesNotSkipOpenGate() {
        val fresh = SessionTracker.startSession(pkg, now)
        assertFalse(
            OpenGatePassPolicy.openGateAlreadySatisfied(
                session = fresh,
                nowEpochMs = now,
                packageName = pkg,
                inMemoryPassedPackage = pkg,
            ),
        )
    }

    @Test
    fun startFreshSession_staleInMemoryPass_ruleEngineStillReturnsOpenDeterrent() {
        val fresh = SessionTracker.startSession(pkg, now)
        val result = RuleEngine.evaluate(
            RuleEvaluationContext(
                nowEpochMs = now,
                packageName = pkg,
                profile = profile,
                limit = limit,
                isMonitored = true,
                usage = UsageSnapshot(),
                sessionState = fresh,
                pauses = emptyList(),
                enforcementConfig = profile.enforcementConfig(),
            ),
            openAlreadyPassed = OpenGatePassPolicy.openGateAlreadySatisfied(
                session = fresh,
                nowEpochMs = now,
                packageName = pkg,
                inMemoryPassedPackage = pkg,
            ),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun startFreshDecision_clearsStaleMemoryBeforeEvaluate() {
        val awayMs = SessionContinuityPolicy.RESUME_GRACE_MS + 60_000L
        val existing = SessionState(
            packageName = pkg,
            sessionStartEpochMs = now - awayMs,
            openGatePassedEpochMs = now - awayMs + 1_000L,
            devicePausedAtEpochMs = now - awayMs,
        )
        val action = SessionStartDecision.decide(
            existingState = existing,
            nowEpochMs = now,
            dayStartEpochMs = 0L,
            liveInMemorySession = false,
        )
        assertTrue(action == SessionStartDecision.Action.StartFresh)
        val fresh = SessionTracker.startSession(pkg, now)
        assertFalse(
            OpenGatePassPolicy.openGateAlreadySatisfied(
                session = fresh,
                nowEpochMs = now,
                packageName = pkg,
                inMemoryPassedPackage = pkg,
            ),
        )
    }
}
