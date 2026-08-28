package com.gatekeep.domain

import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.WarningLevel

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

        val config = context.enforcementConfig

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
                bypassAllowed = false,
            )
        }

        if (!ScheduleEvaluator.isWithinAllowedWindow(
                windows = context.scheduleWindows,
                packageName = context.packageName,
                profileId = context.profile.id,
                nowEpochMs = context.nowEpochMs,
            ) && context.scheduleWindows.isNotEmpty()
        ) {
            return RuleResult.Blocked(
                reason = BlockReason.outsideSchedule,
                bypassAllowed = false,
            )
        }

        val sessionResult = SessionTracker.evaluateSession(
            limit = context.limit,
            session = context.sessionState,
            nowEpochMs = context.nowEpochMs,
        )
        when (sessionResult) {
            is SessionTracker.SessionCheckResult.OnBreak -> {
                return applySessionLimitAction(
                    config = config,
                    reason = BlockReason.onBreak,
                    breakUntilEpochMs = sessionResult.breakUntilEpochMs,
                )
            }
            is SessionTracker.SessionCheckResult.SessionExceeded -> {
                return applySessionLimitAction(
                    config = config,
                    reason = BlockReason.sessionLimit,
                    breakUntilEpochMs = sessionResult.breakUntilEpochMs,
                )
            }
            is SessionTracker.SessionCheckResult.Allowed -> { /* continue */ }
        }

        val limitResult = LimitEvaluator.evaluate(context.limit, context.usage)
        when (limitResult) {
            is LimitEvaluator.LimitCheckResult.Blocked -> {
                return applyLimitAction(config, limitResult.reason)
            }
            is LimitEvaluator.LimitCheckResult.Allowed -> {
                val sessionAllowed = sessionResult as SessionTracker.SessionCheckResult.Allowed
                val openResult = evaluateOnOpen(config, context.profile)
                if (openResult != null) return openResult

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

    private fun applyLimitAction(
        config: ProfileEnforcementConfig,
        reason: BlockReason,
    ): RuleResult = when (config.onLimitAction) {
        OnLimitAction.notifyOnly -> RuleResult.Allowed(
            remainingDailyMs = 0L,
            remainingSessionMs = null,
            remainingHourlyMs = null,
            remainingWeeklyMs = null,
            notifyLimitReached = true,
            notifyLimitReason = reason,
        )
        OnLimitAction.limitWithExtensions -> RuleResult.Blocked(
            reason = reason,
            bypassAllowed = true,
        )
        OnLimitAction.hardBlock -> RuleResult.Blocked(
            reason = reason,
            bypassAllowed = false,
        )
    }

    private fun applySessionLimitAction(
        config: ProfileEnforcementConfig,
        reason: BlockReason,
        breakUntilEpochMs: Long?,
    ): RuleResult = when (config.onSessionLimitAction) {
        OnSessionLimitAction.notifyOnly -> RuleResult.Allowed(
            remainingDailyMs = null,
            remainingSessionMs = 0L,
            remainingHourlyMs = null,
            remainingWeeklyMs = null,
            notifyLimitReached = true,
            notifyLimitReason = reason,
        )
        OnSessionLimitAction.deterrentMath -> RuleResult.Blocked(
            reason = reason,
            breakUntilEpochMs = breakUntilEpochMs,
            bypassAllowed = true,
            sessionDeterrent = FrictionMethod.math,
        )
        OnSessionLimitAction.deterrentWait -> RuleResult.Blocked(
            reason = reason,
            breakUntilEpochMs = breakUntilEpochMs,
            bypassAllowed = true,
            sessionDeterrent = FrictionMethod.waitOneMin,
        )
        OnSessionLimitAction.limitWithExtensions -> RuleResult.Blocked(
            reason = reason,
            breakUntilEpochMs = breakUntilEpochMs,
            bypassAllowed = true,
        )
        OnSessionLimitAction.hardBlock -> RuleResult.Blocked(
            reason = reason,
            breakUntilEpochMs = breakUntilEpochMs,
            bypassAllowed = false,
        )
    }

    private fun evaluateOnOpen(
        config: ProfileEnforcementConfig,
        profile: com.gatekeep.domain.model.Profile,
    ): RuleResult? = when (config.onOpenAction) {
        OnOpenAction.none -> {
            if (profile.delayOpenSeconds > 0) {
                RuleResult.DelayOpen(profile.delayOpenSeconds)
            } else {
                null
            }
        }
        OnOpenAction.pinGate -> null
        OnOpenAction.deterrentMath -> RuleResult.OpenDeterrent(
            method = FrictionMethod.math,
        )
        OnOpenAction.deterrentWait -> RuleResult.OpenDeterrent(
            method = FrictionMethod.waitOneMin,
        )
    }
}
