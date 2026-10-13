package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.LimitExtensionBonus
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.RuleEvaluation
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue 01: daily-limit overlay must not present while daily remaining is still positive.
 * Samsung log: overlay_show dailyLimit after unlock/usage_paused while HUD still had
 * session remaining; Current Usage later showed 5h15m / 6h30m.
 */
class DailyLimitPresentationTest {

    private val youtube = "app.revanced.android.youtube"
    private val now = 1_791_757_377_671L
    private val sessionRemainingMs = 106_862L
    private val dailyUsedMs = 5 * 60 * 60_000L + 15 * 60_000L
    private val dailyCapMs = 6 * 60 * 60_000L + 30 * 60_000L
    private val dailyRemainingMs = dailyCapMs - dailyUsedMs

    private val profile = Profile(
        id = 1,
        name = "Test",
        isActive = true,
        onLimitAction = OnLimitAction.limitWithExtensions,
        onSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
        sessionLimitMs = 15 * 60_000L,
        dailyLimitMs = dailyCapMs,
    )
    private val limit = AppLimit(
        profileId = 1,
        packageName = youtube,
        dailyLimitMs = dailyCapMs,
        sessionLimitMs = 15 * 60_000L,
        enabled = true,
    )

    @Test
    fun merge_doesNotPresentDailyLimit_whenRemainingDailyIsPositive() {
        val evaluation = RuleEvaluation(
            session = RuleResult.Allowed(
                remainingDailyMs = null,
                remainingSessionMs = sessionRemainingMs,
                remainingHourlyMs = null,
                remainingWeeklyMs = null,
            ),
            period = RuleResult.Blocked(BlockReason.dailyLimit, bypassAllowed = true),
            dailyUsedMs = dailyUsedMs,
            dailyCapMs = dailyCapMs,
            remainingDailyMs = dailyRemainingMs,
        )

        val result = RuleEngine.mergeForPresentation(
            evaluation = evaluation,
            config = profile.enforcementConfig(),
            openAlreadyPassed = true,
        )

        assertNotEquals(BlockReason.dailyLimit, (result as? RuleResult.Blocked)?.reason)
        assertInstanceOf(RuleResult.Allowed::class.java, result)
        assertEquals(dailyRemainingMs, (result as RuleResult.Allowed).remainingDailyMs)
        assertEquals(sessionRemainingMs, result.remainingSessionMs)
    }

    @Test
    fun merge_mayPresentDailyLimit_whenRemainingDailyIsExhausted() {
        val evaluation = RuleEvaluation(
            session = RuleResult.Allowed(
                remainingDailyMs = null,
                remainingSessionMs = sessionRemainingMs,
                remainingHourlyMs = null,
                remainingWeeklyMs = null,
            ),
            period = RuleResult.Blocked(BlockReason.dailyLimit, bypassAllowed = true),
            dailyUsedMs = dailyCapMs + 1,
            dailyCapMs = dailyCapMs,
            remainingDailyMs = 0L,
        )

        val result = RuleEngine.mergeForPresentation(
            evaluation = evaluation,
            config = profile.enforcementConfig(),
            openAlreadyPassed = true,
        )

        assertInstanceOf(RuleResult.Blocked::class.java, result)
        assertEquals(BlockReason.dailyLimit, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun evaluate_fiveHoursFifteenOfSixHoursThirty_doesNotPresentDailyLimit() {
        val session = SessionTracker.startSession(
            youtube,
            now - (15 * 60_000L - sessionRemainingMs),
        )
        val result = RuleEngine.evaluate(
            RuleEvaluationContext(
                nowEpochMs = now,
                packageName = youtube,
                profile = profile,
                limit = limit,
                isMonitored = true,
                usage = UsageSnapshot(dailyMs = dailyUsedMs),
                sessionState = session,
                pauses = emptyList(),
                enforcementConfig = profile.enforcementConfig(),
            ),
            openAlreadyPassed = true,
        )

        assertNotEquals(BlockReason.dailyLimit, (result as? RuleResult.Blocked)?.reason)
        assertInstanceOf(RuleResult.Allowed::class.java, result)
        assertEquals(dailyRemainingMs, (result as RuleResult.Allowed).remainingDailyMs)
    }

    @Test
    fun evaluate_displayCapStillHasBudget_doesNotPresentDailyLimit() {
        val baseMs = 6 * 60 * 60_000L
        val bonusMs = 15 * 60_000L
        val anchorMs = baseMs + 15 * 60_000L
        val usedMs = baseMs + 20 * 60_000L
        val displayCapMs = anchorMs + bonusMs
        val result = RuleEngine.evaluate(
            RuleEvaluationContext(
                nowEpochMs = now,
                packageName = youtube,
                profile = profile.copy(dailyLimitMs = baseMs),
                limit = limit.copy(dailyLimitMs = baseMs),
                isMonitored = true,
                usage = UsageSnapshot(dailyMs = usedMs),
                sessionState = null,
                pauses = emptyList(),
                enforcementConfig = profile.enforcementConfig(),
                limitExtensionBonus = LimitExtensionBonus(
                    dailyMs = bonusMs,
                    dailyAnchorMs = anchorMs,
                ),
            ),
            openAlreadyPassed = true,
        )

        assertNotEquals(BlockReason.dailyLimit, (result as? RuleResult.Blocked)?.reason)
        assertInstanceOf(RuleResult.Allowed::class.java, result)
        assertEquals(displayCapMs - usedMs, (result as RuleResult.Allowed).remainingDailyMs)
    }

    @Test
    fun evaluateAll_exposesDailyUsedCapRemaining() {
        val evaluation = RuleEngine.evaluateAll(
            RuleEvaluationContext(
                nowEpochMs = now,
                packageName = youtube,
                profile = profile,
                limit = limit,
                isMonitored = true,
                usage = UsageSnapshot(dailyMs = dailyUsedMs),
                sessionState = null,
                pauses = emptyList(),
                enforcementConfig = profile.enforcementConfig(),
            ),
        )

        assertEquals(dailyUsedMs, evaluation.dailyUsedMs)
        assertEquals(dailyCapMs, evaluation.dailyCapMs)
        assertEquals(dailyRemainingMs, evaluation.remainingDailyMs)
        assertTrue(evaluation.remainingDailyMs!! > 0L)
    }
}
