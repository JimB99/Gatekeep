package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SessionState
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class RulesInteractionTest {

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

    @Test
    fun openWait_plus_dailyHardBlock_suppressesOpen() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.hardBlock,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                usage = UsageSnapshot(dailyMs = 60 * 60_000L + 1),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.dailyLimit, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun openWait_plus_dailyExtensions_showsOpenFirst() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.limitWithExtensions,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                usage = UsageSnapshot(dailyMs = 60 * 60_000L + 1),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun openWait_plus_sessionHardBlock_suppressesOpen() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onSessionLimitAction = OnSessionLimitAction.hardBlock,
        )
        val session = SessionTracker.startSession("com.test.app", 1_000_000L - 20 * 60_000L)
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                now = 1_000_000L,
                sessionState = session,
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.sessionLimit, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun openWait_plus_dailyNotifyOnly_showsOpenFirst() {
        val profile = profileBase.copy(
            onOpenAction = OnOpenAction.deterrentWait,
            onLimitAction = OnLimitAction.notifyOnly,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                usage = UsageSnapshot(dailyMs = 60 * 60_000L + 1),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun noLimitToday_plus_sessionHardBlock_stillBlocksSession() {
        val profile = profileBase.copy(onSessionLimitAction = OnSessionLimitAction.hardBlock)
        val session = SessionTracker.startSession("com.test.app", 1_000_000L - 20 * 60_000L)
        val pause = Pause(
            profileId = 1,
            packageName = "com.test.app",
            type = PauseType.noLimitToday,
            untilEpochMs = Long.MAX_VALUE,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                now = 1_000_000L,
                sessionState = session,
                usage = UsageSnapshot(dailyMs = 60 * 60_000L + 1),
                pauses = listOf(pause),
                periodLimitsDisabled = true,
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.sessionLimit, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun noLimitToday_plus_openWait_stillShowsOpen() {
        val profile = profileBase.copy(onOpenAction = OnOpenAction.deterrentWait)
        val pause = Pause(
            profileId = 1,
            packageName = "com.test.app",
            type = PauseType.noLimitToday,
            untilEpochMs = Long.MAX_VALUE,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                usage = UsageSnapshot(dailyMs = 60 * 60_000L + 1),
                pauses = listOf(pause),
                periodLimitsDisabled = true,
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.OpenDeterrent::class.java, result)
    }

    @Test
    fun extensionGracePause_doesNotBypassSessionLimit() {
        val profile = profileBase.copy(onSessionLimitAction = OnSessionLimitAction.hardBlock)
        val session = SessionTracker.startSession("com.test.app", 1_000_000L - 20 * 60_000L)
        val gracePause = Pause(
            profileId = 1,
            packageName = "com.test.app",
            type = PauseType.extensionGrace,
            untilEpochMs = Long.MAX_VALUE,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                now = 1_000_000L,
                sessionState = session,
                pauses = listOf(gracePause),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.sessionLimit, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun noLimitToday_pause_doesNotBypassUserPauseSemantics() {
        val profile = profileBase.copy(onLimitAction = OnLimitAction.hardBlock)
        val userPause = PauseManager.createPause(
            type = PauseType.fifteenMin,
            nowEpochMs = 1000,
            profileId = 1,
        )
        val result = RuleEngine.evaluate(
            context(
                profile = profile,
                usage = UsageSnapshot(dailyMs = 60 * 60_000L + 1),
                pauses = listOf(userPause.copy(untilEpochMs = Long.MAX_VALUE)),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )
        assertInstanceOf(RuleResult.Allowed::class.java, result)
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("meaningfulTriples")
    fun combinedTriple_dailyCap_respectsStrictest(triple: RulesTriple) {
        val profile = profileBase.copy(
            onOpenAction = triple.open,
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
            OnLimitAction.hardBlock, OnLimitAction.mandatoryBreak -> {
                assertInstanceOf(RuleResult.Blocked::class.java, result)
            }
            OnLimitAction.notifyOnly -> {
                if (triple.open == OnOpenAction.deterrentMath || triple.open == OnOpenAction.deterrentWait) {
                    assertTrue(
                        result is RuleResult.OpenDeterrent || result is RuleResult.DelayOpen,
                    )
                } else {
                    assertInstanceOf(RuleResult.Allowed::class.java, result)
                }
            }
            else -> {
                assertTrue(
                    result is RuleResult.Blocked ||
                        result is RuleResult.OpenDeterrent ||
                        result is RuleResult.DelayOpen,
                )
            }
        }
    }

    private fun context(
        profile: Profile = profileBase,
        now: Long = 1_000_000L,
        usage: UsageSnapshot = UsageSnapshot(),
        sessionState: SessionState? = null,
        pauses: List<Pause> = emptyList(),
        periodLimitsDisabled: Boolean = false,
        enforcementConfig: ProfileEnforcementConfig = profile.enforcementConfig(),
    ) = RuleEvaluationContext(
        nowEpochMs = now,
        packageName = "com.test.app",
        profile = profile,
        limit = limit,
        isMonitored = true,
        usage = usage,
        sessionState = sessionState,
        pauses = pauses,
        enforcementConfig = enforcementConfig,
        periodLimitsDisabled = periodLimitsDisabled,
    )

    data class RulesTriple(
        val open: OnOpenAction,
        val limit: OnLimitAction,
        val session: OnSessionLimitAction,
        val label: String,
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
    }
}
