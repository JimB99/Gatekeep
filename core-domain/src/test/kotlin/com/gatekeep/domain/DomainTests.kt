package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.ResolvedSchedulePolicy
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.PolicySource
import com.gatekeep.domain.model.ScheduleWindow
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `merged break includes explicit zero`() {
        val merged = ProfileMergeEngine.mergedLimitForApp(
            listOf(
                AppLimit(1, "com.test", dailyLimitMs = 3600_000, breakDurationMs = 0L, enabled = true),
                AppLimit(2, "com.test", dailyLimitMs = 1800_000, breakDurationMs = 300_000, enabled = true),
            ),
            "com.test",
        )
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
    fun `blocks on schedule block policy`() {
        val result = RuleEngine.evaluate(
            baseContext(
                resolvedSchedulePolicy = ResolvedSchedulePolicy(
                    mode = SchedulePolicyMode.block,
                    limits = null,
                    enforcementConfig = null,
                    source = PolicySource.noScheduleMatch,
                ),
            ),
        )
        assertTrue(result is RuleResult.Blocked)
        assertEquals(BlockReason.scheduleBlock, (result as RuleResult.Blocked).reason)
    }

    @Test
    fun `allow policy bypasses limits`() {
        val result = RuleEngine.evaluate(
            baseContext(
                usage = UsageSnapshot(dailyMs = 999 * 60_000L),
                resolvedSchedulePolicy = ResolvedSchedulePolicy(
                    mode = SchedulePolicyMode.allow,
                    limits = null,
                    enforcementConfig = null,
                    source = PolicySource.segment,
                ),
            ),
        )
        assertTrue(result is RuleResult.Allowed)
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

    @Test
    fun `focus block returns blocked before pause allow`() {
        val focusBlock = PauseManager.createPause(
            type = com.gatekeep.domain.model.PauseType.focusBlock,
            nowEpochMs = 1000,
            profileId = 1,
            untilEpochMs = Long.MAX_VALUE,
        )
        val allowPause = PauseManager.createPause(
            type = com.gatekeep.domain.model.PauseType.fifteenMin,
            nowEpochMs = 1000,
            profileId = 1,
        )
        val blocked = RuleEngine.evaluate(
            baseContext(
                usage = UsageSnapshot(dailyMs = 999 * 60_000L),
                pauses = listOf(
                    focusBlock,
                    allowPause.copy(untilEpochMs = Long.MAX_VALUE),
                ),
            ),
        )
        assertTrue(blocked is RuleResult.Blocked)
        assertEquals(BlockReason.focusMode, (blocked as RuleResult.Blocked).reason)
    }

    @Test
    fun `notify only on limit returns allowed with flag`() {
        val notifyProfile = profile.copy(onLimitAction = OnLimitAction.notifyOnly)
        val result = RuleEngine.evaluate(
            baseContext(
                profile = notifyProfile,
                usage = UsageSnapshot(dailyMs = 61 * 60_000L),
                enforcementConfig = notifyProfile.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.Allowed)
        val allowed = result as RuleResult.Allowed
        assertTrue(allowed.notifyLimitReached)
    }

    @Test
    fun `hard block on limit disallows bypass`() {
        val hardProfile = profile.copy(onLimitAction = OnLimitAction.hardBlock)
        val result = RuleEngine.evaluate(
            baseContext(
                profile = hardProfile,
                usage = UsageSnapshot(dailyMs = 61 * 60_000L),
                enforcementConfig = hardProfile.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.Blocked)
        assertEquals(false, (result as RuleResult.Blocked).bypassAllowed)
    }

    @Test
    fun `open deterrent when configured`() {
        val openProfile = profile.copy(onOpenAction = OnOpenAction.deterrentMath)
        val result = RuleEngine.evaluate(
            baseContext(
                profile = openProfile,
                enforcementConfig = openProfile.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.OpenDeterrent)
    }

    @Test
    fun `mandatory break on limit sets break until`() {
        val breakProfile = profile.copy(
            onLimitAction = OnLimitAction.mandatoryBreak,
            limitBreakDurationMs = 5 * 60_000L,
        )
        val now = 1_000_000L
        val result = RuleEngine.evaluate(
            baseContext(
                profile = breakProfile,
                now = now,
                usage = UsageSnapshot(dailyMs = 61 * 60_000L),
                enforcementConfig = breakProfile.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.Blocked)
        val blocked = result as RuleResult.Blocked
        assertEquals(false, blocked.bypassAllowed)
        assertTrue(blocked.breakUntilEpochMs != null)
    }

    @Test
    fun `deterrent on limit does not set break until`() {
        val deterrentProfile = profile.copy(onLimitAction = OnLimitAction.deterrentMath)
        val result = RuleEngine.evaluate(
            baseContext(
                profile = deterrentProfile,
                usage = UsageSnapshot(dailyMs = 61 * 60_000L),
                enforcementConfig = deterrentProfile.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.Blocked)
        assertNull((result as RuleResult.Blocked).breakUntilEpochMs)
    }

    @Test
    fun `mandatory break on session sets break until`() {
        val breakProfile = profile.copy(onSessionLimitAction = com.gatekeep.domain.model.OnSessionLimitAction.mandatoryBreak)
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test.app", now - 20 * 60_000L)
        val result = RuleEngine.evaluate(
            baseContext(
                profile = breakProfile,
                now = now,
                sessionState = session,
                enforcementConfig = breakProfile.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.Blocked)
        val blocked = result as RuleResult.Blocked
        assertEquals(BlockReason.sessionLimit, blocked.reason)
        assertTrue(blocked.breakUntilEpochMs != null)
    }

    @Test
    fun `deterrent on session does not set break until`() {
        val deterrentProfile = profile.copy(
            onSessionLimitAction = com.gatekeep.domain.model.OnSessionLimitAction.deterrentMath,
        )
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test.app", now - 20 * 60_000L)
        val result = RuleEngine.evaluate(
            baseContext(
                profile = deterrentProfile,
                now = now,
                sessionState = session,
                enforcementConfig = deterrentProfile.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.Blocked)
        assertNull((result as RuleResult.Blocked).breakUntilEpochMs)
    }

    private fun baseContext(
        now: Long = 1_000_000L,
        profile: Profile = this.profile,
        usage: UsageSnapshot = UsageSnapshot(),
        resolvedSchedulePolicy: ResolvedSchedulePolicy? = null,
        pauses: List<com.gatekeep.domain.model.Pause> = emptyList(),
        enforcementConfig: ProfileEnforcementConfig = profile.enforcementConfig(),
        sessionState: com.gatekeep.domain.model.SessionState? = null,
    ) = RuleEvaluationContext(
        nowEpochMs = now,
        packageName = "com.test.app",
        profile = profile,
        limit = limit,
        isMonitored = true,
        usage = usage,
        sessionState = sessionState,
        pauses = pauses,
        resolvedSchedulePolicy = resolvedSchedulePolicy,
        enforcementConfig = enforcementConfig,
    )
}

class ExtensionPolicyEvaluatorTest {

  @Test
  fun `allows configured minute option`() {
    val policy = com.gatekeep.domain.model.ExtensionPolicy(optionMinutes = listOf(1, 5, 10))
    val result = ExtensionPolicyEvaluator.evaluateExtension(policy, 5, overridesToday = 0, consecutiveInSession = 0)
    assertTrue(result is ExtensionPolicyEvaluator.ExtensionDecision.Allowed)
  }

  @Test
  fun `denies when daily cap reached`() {
    val policy = com.gatekeep.domain.model.ExtensionPolicy(
      optionMinutes = listOf(5),
      maxExtensionsPerDay = 2,
    )
    val result = ExtensionPolicyEvaluator.evaluateExtension(policy, 5, overridesToday = 2, consecutiveInSession = 0)
    assertTrue(result is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
  }

  @Test
  fun `denies consecutive cap`() {
    val policy = com.gatekeep.domain.model.ExtensionPolicy(
      optionMinutes = listOf(5),
      maxConsecutiveExtensions = 1,
    )
    val result = ExtensionPolicyEvaluator.evaluateExtension(policy, 5, overridesToday = 0, consecutiveInSession = 1)
    assertTrue(result is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
  }

  @Test
  fun `consecutive cap cannot exceed daily cap`() {
    val policy = com.gatekeep.domain.model.ExtensionPolicy(
      optionMinutes = listOf(5),
      maxExtensionsPerDay = 2,
      maxConsecutiveExtensions = 5,
    )
    val result = ExtensionPolicyEvaluator.evaluateExtension(policy, 5, overridesToday = 0, consecutiveInSession = 2)
    assertTrue(result is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
  }

  @Test
  fun `effectiveConsecutiveCap falls back to daily cap`() {
    val policy = com.gatekeep.domain.model.ExtensionPolicy(maxExtensionsPerDay = 2)
    assertEquals(2, ExtensionPolicyEvaluator.effectiveConsecutiveCap(policy))
  }

  @Test
  fun `daily cap applies as consecutive cap when consecutive unset`() {
    val policy = com.gatekeep.domain.model.ExtensionPolicy(
      optionMinutes = listOf(5),
      maxExtensionsPerDay = 2,
    )
    val allowed = ExtensionPolicyEvaluator.evaluateExtension(policy, 5, overridesToday = 0, consecutiveInSession = 1)
    val denied = ExtensionPolicyEvaluator.evaluateExtension(policy, 5, overridesToday = 0, consecutiveInSession = 2)
    assertTrue(allowed is ExtensionPolicyEvaluator.ExtensionDecision.Allowed)
    assertTrue(denied is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
  }

  @Test
  fun `no limit today when enabled`() {
    val policy = com.gatekeep.domain.model.ExtensionPolicy(showNoLimitToday = true)
    val result = ExtensionPolicyEvaluator.evaluateExtension(
      policy, 0, 0, 0, isNoLimitTodayRequest = true,
    )
    assertTrue(result is ExtensionPolicyEvaluator.ExtensionDecision.NoLimitToday)
  }
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

    @Test
    fun `session exceeded with zero break has no break until`() {
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test", now - 20 * 60_000L)
        val result = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = 15 * 60_000L, breakDurationMs = 0L),
            session,
            now,
        )
        assertTrue(result is SessionTracker.SessionCheckResult.SessionExceeded)
        val exceeded = result as SessionTracker.SessionCheckResult.SessionExceeded
        assertEquals(null, exceeded.breakUntilEpochMs)
    }

    @Test
    fun `clearBreak removes break until`() {
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test", now).copy(breakUntilEpochMs = now + 60_000L)
        val cleared = SessionTracker.clearBreak(session)
        assertNull(cleared.breakUntilEpochMs)
        assertFalse(SessionTracker.isOnBreak(cleared, now))
    }

    @Test
    fun `completeExpiredBreak starts fresh session after mandatory break ends`() {
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test", now - 20 * 60_000L)
            .copy(breakUntilEpochMs = now - 1_000L)

        val completed = SessionTracker.completeExpiredBreak(session, now)

        assertEquals(now, completed.sessionStartEpochMs)
        assertNull(completed.breakUntilEpochMs)
        assertFalse(SessionTracker.isOnBreak(completed, now))
    }

    @Test
    fun `completeExpiredBreak leaves active break unchanged`() {
        val now = 1_000_000L
        val session = SessionTracker.startSession("com.test", now - 20 * 60_000L)
            .copy(breakUntilEpochMs = now + 60_000L)

        assertEquals(session, SessionTracker.completeExpiredBreak(session, now))
    }

    @Test
    fun `expired break completion allows new session usage`() {
        val now = 1_000_000L
        val sessionLimit = 15 * 60_000L
        val expiredBreak = SessionTracker.startSession("com.test", now - 20 * 60_000L)
            .copy(breakUntilEpochMs = now - 1_000L)
        val completed = SessionTracker.completeExpiredBreak(expiredBreak, now)

        val result = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = sessionLimit, breakDurationMs = 5 * 60_000L),
            completed,
            now,
        )

        assertTrue(result is SessionTracker.SessionCheckResult.Allowed)
        assertEquals(sessionLimit, (result as SessionTracker.SessionCheckResult.Allowed).remainingSessionMs)
    }

    @Test
    fun `excluded and friction time reduce session duration`() {
        val now = 1_000_000L
        var session = SessionTracker.startSession("com.test", now - 10 * 60_000L)
        session = SessionTracker.startFriction(session, now - 2 * 60_000L)
        session = SessionTracker.endFriction(session, now)
        val duration = SessionTracker.sessionDurationMs(session, now)
        assertEquals(8 * 60_000L, duration)
    }

    @Test
    fun `extension bonus via excluded time grants more session remaining`() {
        val now = 1_000_000L
        val sessionLimit = 15 * 60_000L
        val session = SessionTracker.startSession("com.test", now - sessionLimit)
        val atLimit = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = sessionLimit),
            session,
            now,
        )
        assertTrue(atLimit is SessionTracker.SessionCheckResult.SessionExceeded)

        val extended = SessionTracker.addExcludedTime(session, 5 * 60_000L)
        val afterExtension = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = sessionLimit),
            extended,
            now,
        )
        assertTrue(afterExtension is SessionTracker.SessionCheckResult.Allowed)
        val allowed = afterExtension as SessionTracker.SessionCheckResult.Allowed
        assertEquals(5 * 60_000L, allowed.remainingSessionMs)
    }

    @Test
    fun `large extension bonus exceeds original session limit`() {
        val now = 1_000_000L
        val sessionLimit = 60_000L
        val extensionMs = 150 * 60_000L
        val session = SessionTracker.startSession("com.test", now - sessionLimit)
        val extended = SessionTracker.addExcludedTime(session, extensionMs)
        val result = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = sessionLimit),
            extended,
            now,
        )
        assertTrue(result is SessionTracker.SessionCheckResult.Allowed)
        val allowed = result as SessionTracker.SessionCheckResult.Allowed
        assertTrue(allowed.remainingSessionMs!! >= extensionMs - 1000)
    }

    @Test
    fun `session counts time during open wait when friction is not excluded`() {
        val now = 1_000_000L
        val sessionLimit = 5 * 60_000L
        val session = SessionTracker.startSession("com.test", now - 30_000L)
        val result = SessionTracker.evaluateSession(
            AppLimit(1, "com.test", sessionLimitMs = sessionLimit),
            session,
            now,
        )
        assertTrue(result is SessionTracker.SessionCheckResult.Allowed)
        val allowed = result as SessionTracker.SessionCheckResult.Allowed
        assertEquals(sessionLimit - 30_000L, allowed.remainingSessionMs)
    }
    @Test
    fun `pending wait blocks until deadline`() {
        val now = 1_000_000L
        val session = SessionTracker.setPendingWait(
            SessionTracker.startSession("com.test", now),
            now + 60_000L,
        )
        assertTrue(SessionTracker.hasPendingWait(session, now + 30_000L))
        assertFalse(SessionTracker.hasPendingWait(session, now + 60_000L))
        assertEquals(30_000L, SessionTracker.pendingWaitRemainingMs(session, now + 30_000L))
    }

    @Test
    fun `clear pending wait removes deadline`() {
        val now = 1_000_000L
        val session = SessionTracker.setPendingWait(
            SessionTracker.startSession("com.test", now),
            now + 60_000L,
        )
        val cleared = SessionTracker.clearPendingWait(session)
        assertFalse(SessionTracker.hasPendingWait(cleared, now + 30_000L))
    }

    @Test
    fun `session limit notified flag set and cleared on new session`() {
        val now = 1_000_000L
        var session = SessionTracker.startSession("com.test", now)
        assertFalse(session.sessionLimitNotified)
        session = SessionTracker.markSessionLimitNotified(session)
        assertTrue(session.sessionLimitNotified)
        val newSession = SessionTracker.startSession("com.test", now + 1000)
        assertFalse(newSession.sessionLimitNotified)
    }
}

class FocusBlockManagerTest {

    @Test
    fun `global focus block applies to any profile`() {
        val pause = PauseManager.createPause(
            type = com.gatekeep.domain.model.PauseType.focusBlock,
            nowEpochMs = 1000,
            untilEpochMs = 2000,
        )
        val check = FocusBlockManager.isBlocked(listOf(pause), profileId = 99, nowEpochMs = 1500)
        assertTrue(check is FocusBlockManager.BlockCheck.Blocked)
    }

    @Test
    fun `profile focus block does not apply to other profiles`() {
        val pause = PauseManager.createPause(
            type = com.gatekeep.domain.model.PauseType.focusBlock,
            nowEpochMs = 1000,
            profileId = 1,
            untilEpochMs = 2000,
        )
        val check = FocusBlockManager.isBlocked(listOf(pause), profileId = 2, nowEpochMs = 1500)
        assertTrue(check is FocusBlockManager.BlockCheck.NotBlocked)
    }
}

class FrictionChallengeTest {

    @Test
    fun `verify correct answer`() {
        val challenge = FrictionChallenge.generate(com.gatekeep.domain.model.FrictionDifficulty.easy, kotlin.random.Random(42))
        assertTrue(FrictionChallenge.verify(challenge, challenge.answer))
    }
}

class LimitHierarchyTest {

    @Test
    fun `valid when weekly daily hourly ordered`() {
        assertTrue(LimitHierarchy.isValid(7 * 86_400_000L, 3_600_000L, 1_800_000L))
    }

    @Test
    fun `invalid when daily exceeds weekly`() {
        assertFalse(LimitHierarchy.isValid(3_600_000L, 7_200_000L, 0L))
    }

    @Test
    fun `zero tiers are skipped`() {
        assertTrue(LimitHierarchy.isValid(7 * 86_400_000L, 0L, 1_800_000L))
        assertTrue(LimitHierarchy.isValid(0L, 0L, 0L))
    }

    @Test
    fun `session must not exceed hourly`() {
        assertFalse(LimitHierarchy.isValid(0L, 0L, 1_800_000L, 3_600_000L))
    }

    @Test
    fun `valid full hierarchy including session`() {
        assertTrue(LimitHierarchy.isValid(7 * 86_400_000L, 3_600_000L, 1_800_000L, 900_000L))
    }
}
