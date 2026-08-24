package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.ScheduleWindow
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProfileMergeEngineTest {

    @Test
    fun `merged limit picks strictest caps`() {
        val merged = ProfileMergeEngine.mergedLimitForApp(
            listOf(
                AppLimit(1, "com.test", dailyLimitMs = 3600_000, sessionLimitMs = 900_000, enabled = true),
                AppLimit(2, "com.test", dailyLimitMs = 1800_000, sessionLimitMs = 600_000, breakDurationMs = 300_000, enabled = true),
            ),
            "com.test",
        )
        assertEquals(1800_000, merged?.dailyLimitMs)
        assertEquals(600_000, merged?.sessionLimitMs)
        assertEquals(300_000, merged?.breakDurationMs)
    }

    @Test
    fun `merged friction prefers explicit method`() {
        val merged = ProfileMergeEngine.mergedLimitForApp(
            listOf(
                AppLimit(1, "com.test", dailyLimitMs = 1000, frictionMethod = FrictionMethod.math, enabled = true),
                AppLimit(2, "com.test", dailyLimitMs = 500, frictionMethod = FrictionMethod.waitOneMin, enabled = true),
            ),
            "com.test",
        )
        assertTrue(merged?.frictionMethod == FrictionMethod.math || merged?.frictionMethod == FrictionMethod.waitOneMin)
    }
}

class RuleEngineTest {

    private val profile = Profile(id = 1, name = "Default", isActive = true)
    private val limit = AppLimit(
        profileId = 1,
        packageName = "com.test.app",
        dailyLimitMs = 60 * 60_000L,
        sessionLimitMs = 15 * 60_000L,
        breakDurationMs = 5 * 60_000L,
    )

    @Test
    fun `allows when under daily limit`() {
        val result = RuleEngine.evaluate(baseContext(usage = UsageSnapshot(dailyMs = 30 * 60_000L)))
        assertTrue(result is RuleResult.Allowed)
    }

    @Test
    fun `blocks when daily limit exceeded`() {
        val result = RuleEngine.evaluate(baseContext(usage = UsageSnapshot(dailyMs = 61 * 60_000L)))
        assertTrue(result is RuleResult.Blocked)
        assertEquals(BlockReason.dailyLimit, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun `blocks outside schedule window`() {
        val monday10am = ZonedDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val windows = listOf(
            ScheduleWindow(profileId = 1, dayOfWeek = 1, startMinute = 18 * 60, endMinute = 20 * 60),
        )
        val result = RuleEngine.evaluate(
            baseContext(now = monday10am, scheduleWindows = windows),
        )
        assertTrue(result is RuleResult.Blocked)
        assertEquals(BlockReason.outsideSchedule, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun `pause overrides limits`() {
        val pause = PauseManager.createPause(
            type = com.gatekeep.domain.model.PauseType.fifteenMin,
            nowEpochMs = 1000,
            profileId = 1,
        )
        val result = RuleEngine.evaluate(
            baseContext(
                usage = UsageSnapshot(dailyMs = 999 * 60_000L),
                pauses = listOf(pause.copy(untilEpochMs = Long.MAX_VALUE)),
            ),
        )
        assertTrue(result is RuleResult.Allowed)
    }

    private fun baseContext(
        now: Long = 1_000_000L,
        usage: UsageSnapshot = UsageSnapshot(),
        scheduleWindows: List<ScheduleWindow> = emptyList(),
        pauses: List<com.gatekeep.domain.model.Pause> = emptyList(),
    ) = RuleEvaluationContext(
        nowEpochMs = now,
        packageName = "com.test.app",
        profile = profile,
        limit = limit,
        isMonitored = true,
        usage = usage,
        sessionState = null,
        pauses = pauses,
        scheduleWindows = scheduleWindows,
    )
}

class ScheduleEvaluatorTest {

    @Test
    fun `allows inside window`() {
        val monday7pm = ZonedDateTime.of(2025, 1, 6, 19, 0, 0, 0, ZoneId.of("UTC"))
        val windows = listOf(
            ScheduleWindow(profileId = 1, dayOfWeek = 1, startMinute = 18 * 60, endMinute = 20 * 60),
        )
        assertTrue(
            ScheduleEvaluator.isWithinAllowedWindow(
                windows, "com.test", 1, monday7pm.toInstant().toEpochMilli(), ZoneId.of("UTC"),
            ),
        )
    }
}

class SessionTrackerTest {

    @Test
    fun `session duration resets when start time is now`() {
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test", now)
        val result = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = 15 * 60_000L),
            session,
            now + 1000,
        )
        assertTrue(result is SessionTracker.SessionCheckResult.Allowed)
        val allowed = result as SessionTracker.SessionCheckResult.Allowed
        assertTrue(allowed.remainingSessionMs!! > 14 * 60_000L)
    }

    @Test
    fun `session exceeded triggers break`() {
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test", now - 20 * 60_000L)
        val result = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = 15 * 60_000L, breakDurationMs = 5 * 60_000L),
            session,
            now,
        )
        assertTrue(result is SessionTracker.SessionCheckResult.SessionExceeded)
    }
}

class FrictionChallengeTest {

    @Test
    fun `verify correct answer`() {
        val challenge = FrictionChallenge.generate(com.gatekeep.domain.model.FrictionDifficulty.easy, kotlin.random.Random(42))
        assertTrue(FrictionChallenge.verify(challenge, challenge.answer))
    }
}
