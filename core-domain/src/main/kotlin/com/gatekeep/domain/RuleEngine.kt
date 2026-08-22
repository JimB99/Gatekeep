package com.gatekeep.domain

import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.WarningLevel
import kotlin.random.Random

object RuleEngine {

    fun evaluate(context: RuleEvaluationContext): RuleResult {
        if (!context.isMonitored) {
            return RuleResult.Allowed(
                remainingDailyMs = null,
                remainingSessionMs = null,
                remainingHourlyMs = null,
                remainingWeeklyMs = null,
            )
        }

        if (context.limit == null) {
            return RuleResult.Allowed(null, null, null, null)
        }

        val pauseCheck = PauseManager.isPaused(
            pauses = context.pauses,
            profileId = context.profile.id,
            packageName = context.packageName,
            nowEpochMs = context.nowEpochMs,
        )
        if (pauseCheck is PauseManager.PauseCheck.Paused) {
            return RuleResult.Allowed(
                remainingDailyMs = null,
                remainingSessionMs = null,
                remainingHourlyMs = null,
                remainingWeeklyMs = null,
            )
        }

        if (context.focusModeUntilMs != null && context.nowEpochMs < context.focusModeUntilMs) {
            return RuleResult.Blocked(
                reason = BlockReason.focusMode,
                message = "Focus mode is active",
            )
        }

        if (!ScheduleEvaluator.isWithinAllowedWindow(
                windows = context.scheduleWindows,
                packageName = context.packageName,
                profileId = context.profile.id,
                nowEpochMs = context.nowEpochMs,
            )
        ) {
            return RuleResult.Blocked(
                reason = BlockReason.outsideSchedule,
                message = "Outside allowed time window",
            )
        }

        val sessionResult = SessionTracker.evaluateSession(
            limit = context.limit,
            session = context.sessionState,
            nowEpochMs = context.nowEpochMs,
        )
        when (sessionResult) {
            is SessionTracker.SessionCheckResult.OnBreak -> {
                return RuleResult.Blocked(
                    reason = BlockReason.onBreak,
                    breakUntilEpochMs = sessionResult.breakUntilEpochMs,
                    message = "Take a break",
                )
            }
            is SessionTracker.SessionCheckResult.SessionExceeded -> {
                return RuleResult.Blocked(
                    reason = BlockReason.sessionLimit,
                    breakUntilEpochMs = sessionResult.breakUntilEpochMs,
                    message = "Session limit reached",
                )
            }
            is SessionTracker.SessionCheckResult.Allowed -> { /* continue */ }
        }

        val limitResult = LimitEvaluator.evaluate(context.limit, context.usage)
        when (limitResult) {
            is LimitEvaluator.LimitCheckResult.Blocked -> {
                val reason = when {
                    limitResult.message.contains("Hourly") -> BlockReason.hourlyLimit
                    limitResult.message.contains("Weekly") -> BlockReason.weeklyLimit
                    else -> BlockReason.dailyLimit
                }
                return RuleResult.Blocked(reason = reason, message = limitResult.message)
            }
            is LimitEvaluator.LimitCheckResult.Allowed -> {
                val sessionAllowed = sessionResult as SessionTracker.SessionCheckResult.Allowed
                if (context.profile.delayOpenSeconds > 0) {
                    return RuleResult.DelayOpen(
                        delaySeconds = context.profile.delayOpenSeconds,
                        message = "Wait before opening",
                    )
                }
                return RuleResult.Allowed(
                    remainingDailyMs = limitResult.remainingDailyMs,
                    remainingSessionMs = sessionAllowed.remainingSessionMs,
                    remainingHourlyMs = limitResult.remainingHourlyMs,
                    remainingWeeklyMs = limitResult.remainingWeeklyMs,
                    warningLevel = limitResult.warningLevel,
                )
            }
        }
    }
}
