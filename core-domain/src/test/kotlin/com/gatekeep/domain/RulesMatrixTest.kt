package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SessionState
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class RulesMatrixTest {

    private val profileBase = Profile(
        id = 1,
        name = "Test",
        isActive = true,
        limitBreakDurationMs = 5 * 60_000L,
        breakDurationMs = 5 * 60_000L,
    )
    private val limit = AppLimit(
        profileId = 1,
        packageName = "com.test.app",
        dailyLimitMs = 60 * 60_000L,
        sessionLimitMs = 15 * 60_000L,
        breakDurationMs = 5 * 60_000L,
        enabled = true,
    )

    data class RulesTriple(
        val open: OnOpenAction,
        val limit: OnLimitAction,
        val session: OnSessionLimitAction,
        val label: String,
    )

    @ParameterizedTest(name = "{3}")
    @MethodSource("meaningfulTriples")
    fun evaluateDailyCap_respectsLimitAction(triple: RulesTriple) {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.none,
            onLimitAction = triple.limit,
            onSessionLimitAction = triple.session,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                usage = UsageSnapshot(dailyMs = 60 * 60_000L + 1),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        when (triple.limit) {
            OnLimitAction.notifyOnly -> {
                assertInstanceOf(RuleResult.Allowed::class.java, result)
                assertTrue((result as RuleResult.Allowed).notifyLimitReached)
            }
            OnLimitAction.hardBlock -> {
                assertInstanceOf(RuleResult.Blocked::class.java, result)
                assertEquals(BlockReason.dailyLimit, (result as RuleResult.Blocked).reason)
                assertTrue(!result.bypassAllowed)
            }
            OnLimitAction.mandatoryBreak -> {
                assertInstanceOf(RuleResult.Blocked::class.java, result)
                assertTrue((result as RuleResult.Blocked).breakUntilEpochMs != null)
            }
            else -> assertInstanceOf(RuleResult.Blocked::class.java, result)
        }
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("meaningfulTriples")
    fun evaluateSessionCap_respectsSessionAction(triple: RulesTriple) {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.none,
            onLimitAction = OnLimitAction.notifyOnly,
            onSessionLimitAction = triple.session,
        )
        val session = SessionTracker.startSession("com.test.app", 1_000_000L - 20 * 60_000L)
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                now = 1_000_000L,
                sessionState = session,
                usage = UsageSnapshot(),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        when (triple.session) {
            OnSessionLimitAction.notifyOnly -> {
                assertInstanceOf(RuleResult.Allowed::class.java, result)
                assertTrue((result as RuleResult.Allowed).notifyLimitReached)
            }
            OnSessionLimitAction.hardBlock -> {
                assertInstanceOf(RuleResult.Blocked::class.java, result)
                assertEquals(BlockReason.sessionLimit, (result as RuleResult.Blocked).reason)
            }
            OnSessionLimitAction.mandatoryBreak -> {
                assertInstanceOf(RuleResult.Blocked::class.java, result)
                assertTrue((result as RuleResult.Blocked).breakUntilEpochMs != null)
            }
            OnSessionLimitAction.deterrentMath -> {
                assertInstanceOf(RuleResult.Blocked::class.java, result)
                assertNull((result as RuleResult.Blocked).breakUntilEpochMs)
            }
            else -> assertInstanceOf(RuleResult.Blocked::class.java, result)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("openActions")
    fun evaluateOpenGate_returnsOpenDeterrentWhenConfigured(open: OnOpenAction) {
        val profile = profileBase.copy(onOpenAction = open)
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        when (open) {
            OnOpenAction.none, OnOpenAction.pinGate ->
                assertInstanceOf(RuleResult.Allowed::class.java, result)
            OnOpenAction.deterrentMath, OnOpenAction.deterrentWait ->
                assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
        }
    }

    @org.junit.jupiter.api.Test
    fun evaluateOpenGate_pinGateWithPassword_returnsOpenDeterrent() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.pinGate,
            passwordHash = "hashed-pin",
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
        assertEquals(FrictionMethod.password, (result as RuleResult.OpenDeterrent).method)
    }

    private fun context(
        profile: Profile = profileBase,
        now: Long = 1_000_000L,
        usage: UsageSnapshot = UsageSnapshot(),
        sessionState: SessionState? = null,
        enforcementConfig: ProfileEnforcementConfig = profile.enforcementConfig(),
    ) = RuleEvaluationContext(
        nowEpochMs = now,
        packageName = "com.test.app",
        profile = profile,
        limit = limit,
        isMonitored = true,
        usage = usage,
        sessionState = sessionState,
        pauses = emptyList(),
        enforcementConfig = enforcementConfig,
    )

    companion object {
        @JvmStatic
        fun meaningfulTriples(): Stream<RulesTriple> = Stream.of(
            RulesTriple(OnOpenAction.none, OnLimitAction.hardBlock, OnSessionLimitAction.notifyOnly, "none_hard_notify"),
            RulesTriple(OnOpenAction.pinGate, OnLimitAction.limitWithExtensions, OnSessionLimitAction.mandatoryBreak, "pin_ext_break"),
            RulesTriple(OnOpenAction.deterrentWait, OnLimitAction.deterrentMath, OnSessionLimitAction.hardBlock, "wait_math_hard"),
            RulesTriple(OnOpenAction.deterrentMath, OnLimitAction.mandatoryBreak, OnSessionLimitAction.limitWithExtensions, "math_break_ext"),
            RulesTriple(OnOpenAction.none, OnLimitAction.notifyOnly, OnSessionLimitAction.hardBlock, "none_notify_hard"),
            RulesTriple(OnOpenAction.pinGate, OnLimitAction.hardBlock, OnSessionLimitAction.notifyOnly, "pin_hard_notify"),
            RulesTriple(OnOpenAction.deterrentWait, OnLimitAction.limitWithExtensions, OnSessionLimitAction.mandatoryBreak, "wait_ext_break"),
            RulesTriple(OnOpenAction.none, OnLimitAction.limitWithExtensions, OnSessionLimitAction.hardBlock, "none_ext_hard"),
            RulesTriple(OnOpenAction.pinGate, OnLimitAction.notifyOnly, OnSessionLimitAction.limitWithExtensions, "pin_notify_ext"),
            RulesTriple(OnOpenAction.deterrentMath, OnLimitAction.hardBlock, OnSessionLimitAction.mandatoryBreak, "math_hard_break"),
        )

        @JvmStatic
        fun openActions(): Stream<OnOpenAction> = Stream.of(*OnOpenAction.entries.toTypedArray())
    }
}
