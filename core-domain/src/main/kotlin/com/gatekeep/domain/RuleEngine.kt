package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.RuleEvaluation
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.WarningLevel

object RuleEngine {

    fun evaluate(context: RuleEvaluationContext, openAlreadyPassed: Boolean = false): RuleResult {
        val evaluation = evaluateAll(context)
        return mergeForPresentation(
            evaluation = evaluation,
            config = context.enforcementConfig,
            openAlreadyPassed = openAlreadyPassed,
        )
    }

    fun evaluateAll(context: RuleEvaluationContext): RuleEvaluation {
        if (!context.isMonitored) {
            return RuleEvaluation()
        }

        val focusBlock = FocusBlockManager.isBlocked(
            pauses = context.pauses,
            profileId = context.profile.id,
            nowEpochMs = context.nowEpochMs,
        )
        if (focusBlock is FocusBlockManager.BlockCheck.Blocked) {
            return RuleEvaluation(
                session = RuleResult.Blocked(
                    reason = BlockReason.focusMode,
                    bypassAllowed = false,
                ),
            )
        }

        if (context.focusModeUntilMs != null && context.nowEpochMs < context.focusModeUntilMs) {
            return RuleEvaluation(
                session = RuleResult.Blocked(
                    reason = BlockReason.focusMode,
                    bypassAllowed = false,
                ),
            )
        }

        val pauseCheck = PauseManager.isFullEnforcementPaused(
            pauses = context.pauses,
            profileId = context.profile.id,
            packageName = context.packageName,
            nowEpochMs = context.nowEpochMs,
        )
        if (pauseCheck is PauseManager.PauseCheck.Paused) {
            return RuleEvaluation()
        }

        val schedulePolicy = context.resolvedSchedulePolicy
        if (schedulePolicy != null) {
            when (schedulePolicy.mode) {
                SchedulePolicyMode.allow -> return RuleEvaluation()
                SchedulePolicyMode.block -> {
                    return RuleEvaluation(
                        session = RuleResult.Blocked(
                            reason = BlockReason.scheduleBlock,
                            bypassAllowed = false,
                        ),
                    )
                }
                SchedulePolicyMode.default, SchedulePolicyMode.customize -> { /* continue */ }
            }
        }

        val limit = context.limit
        if (limit == null) {
            return RuleEvaluation()
        }

        val config = context.enforcementConfig

        val sessionAxis = evaluateSessionAxis(
            limit = limit,
            sessionState = context.sessionState,
            nowEpochMs = context.nowEpochMs,
            noLimitTodayActive = context.periodLimitsDisabled,
        )
        val sharedPool = context.profile.limitUsageScope == LimitUsageScope.sharedPool
        val graceRemainingMs = graceRemainingMs(
            pauses = context.pauses,
            profileId = context.profile.id,
            packageName = context.packageName,
            nowEpochMs = context.nowEpochMs,
            sharedPool = sharedPool,
        )
        val periodAxis = evaluatePeriodAxis(
            config = config,
            limit = limit,
            usage = context.usage,
            limitExtensionBonus = context.limitExtensionBonus,
            periodLimitsDisabled = context.periodLimitsDisabled,
            nowEpochMs = context.nowEpochMs,
            sessionState = context.sessionState,
            sessionAllowed = sessionAxis as? SessionTracker.SessionCheckResult.Allowed,
            pauses = context.pauses,
            profileId = context.profile.id,
            packageName = context.packageName,
            sharedPool = sharedPool,
            periodStarts = context.periodStarts,
        )
        val openAxis = evaluateOpenAxis(config, context.profile)
        val dailyCapMs = LimitEvaluator.dailyCapMs(
            limit = limit,
            usage = context.usage,
            extensionBonus = context.limitExtensionBonus,
            graceRemainingMs = graceRemainingMs,
            periodLimitsDisabled = context.periodLimitsDisabled,
        )
        val remainingDailyMs = remainingDailyMs(periodAxis, dailyCapMs, context.usage.dailyMs)
        val periodForPresentation = periodWithoutFalseDailyLimit(
            period = periodAxis,
            remainingDailyMs = remainingDailyMs,
            remainingSessionMs = (sessionAxis as? SessionTracker.SessionCheckResult.Allowed)
                ?.remainingSessionMs,
        )

        return RuleEvaluation(
            open = openAxis,
            session = sessionAxis.toRuleResult(config),
            period = periodForPresentation,
            dailyUsedMs = limit.dailyLimitMs?.let { context.usage.dailyMs },
            dailyCapMs = dailyCapMs,
            remainingDailyMs = remainingDailyMs,
        )
    }

    fun mergeForPresentation(
        evaluation: RuleEvaluation,
        config: ProfileEnforcementConfig,
        openAlreadyPassed: Boolean,
    ): RuleResult {
        val usageWinner = rejectFalseDailyLimit(
            pickUsageWinner(
                session = evaluation.session,
                period = evaluation.period,
                config = config,
            ),
            evaluation,
        )

        if (usageWinner is RuleResult.Blocked && suppressesOpenGate(usageWinner, config)) {
            return usageWinner
        }

        if (!openAlreadyPassed) {
            val openResult = evaluation.open
            if (openResult is RuleResult.OpenDeterrent || openResult is RuleResult.DelayOpen) {
                val openStrictness = openResultStrictness(openResult)
                val usageStrictness = when (usageWinner) {
                    is RuleResult.Blocked -> blockedActionStrictness(usageWinner, config)
                    is RuleResult.Allowed -> if (usageWinner.notifyLimitReached) 0 else -1
                    else -> -1
                }
                if (usageWinner !is RuleResult.Blocked || openStrictness >= usageStrictness) {
                    return openResult
                }
            }
        }

        return when (usageWinner) {
            is RuleResult.Blocked -> usageWinner
            is RuleResult.Allowed -> mergeAllowedResults(
                session = evaluation.session,
                period = evaluation.period,
                usageWinner = usageWinner,
            )
            else -> usageWinner ?: RuleResult.Allowed(null, null, null, null)
        }
    }

    private fun rejectFalseDailyLimit(
        winner: RuleResult?,
        evaluation: RuleEvaluation,
    ): RuleResult? {
        val blocked = winner as? RuleResult.Blocked ?: return winner
        if (blocked.reason != BlockReason.dailyLimit) return winner
        val remaining = evaluation.remainingDailyMs ?: return winner
        if (remaining <= 0L) return winner
        val sessionAllowed = evaluation.session as? RuleResult.Allowed
        return RuleResult.Allowed(
            remainingDailyMs = remaining,
            remainingSessionMs = sessionAllowed?.remainingSessionMs,
            remainingHourlyMs = sessionAllowed?.remainingHourlyMs,
            remainingWeeklyMs = sessionAllowed?.remainingWeeklyMs,
            warningLevel = sessionAllowed?.warningLevel ?: WarningLevel.none,
        )
    }

    private fun remainingDailyMs(
        period: RuleResult?,
        dailyCapMs: Long?,
        dailyUsedMs: Long,
    ): Long? {
        val fromPeriod = (period as? RuleResult.Allowed)?.remainingDailyMs
        if (fromPeriod != null) return fromPeriod
        return dailyCapMs?.let { it - dailyUsedMs }
    }

    private fun periodWithoutFalseDailyLimit(
        period: RuleResult?,
        remainingDailyMs: Long?,
        remainingSessionMs: Long?,
    ): RuleResult? {
        val blocked = period as? RuleResult.Blocked ?: return period
        if (blocked.reason != BlockReason.dailyLimit) return period
        if (remainingDailyMs == null || remainingDailyMs <= 0L) return period
        return RuleResult.Allowed(
            remainingDailyMs = remainingDailyMs,
            remainingSessionMs = remainingSessionMs,
            remainingHourlyMs = null,
            remainingWeeklyMs = null,
        )
    }

    private fun graceRemainingMs(
        pauses: List<Pause>,
        profileId: Long,
        packageName: String,
        nowEpochMs: Long,
        sharedPool: Boolean,
    ): Long? {
        val graceUntil = ExtensionGrantEngine.activeGraceUntilEpochMs(
            pauses = pauses,
            profileId = profileId,
            packageName = packageName,
            nowEpochMs = nowEpochMs,
            sharedPool = sharedPool,
        )
        return graceUntil?.let { (it - nowEpochMs).coerceAtLeast(0) }
    }

    private fun evaluateSessionAxis(
        limit: AppLimit,
        sessionState: com.gatekeep.domain.model.SessionState?,
        nowEpochMs: Long,
        noLimitTodayActive: Boolean,
    ): SessionTracker.SessionCheckResult {
        if (noLimitTodayActive) {
            return SessionTracker.SessionCheckResult.Allowed(remainingSessionMs = null)
        }
        return SessionTracker.evaluateSession(
            limit = limit,
            session = sessionState,
            nowEpochMs = nowEpochMs,
        )
    }

    private fun evaluatePeriodAxis(
        config: ProfileEnforcementConfig,
        limit: AppLimit,
        usage: com.gatekeep.domain.model.UsageSnapshot,
        limitExtensionBonus: com.gatekeep.domain.model.LimitExtensionBonus,
        periodLimitsDisabled: Boolean,
        nowEpochMs: Long,
        sessionState: com.gatekeep.domain.model.SessionState?,
        sessionAllowed: SessionTracker.SessionCheckResult.Allowed?,
        pauses: List<Pause>,
        profileId: Long,
        packageName: String,
        sharedPool: Boolean,
        periodStarts: com.gatekeep.domain.model.PeriodStarts,
    ): RuleResult? {
        if (periodLimitsDisabled) {
            return RuleResult.Allowed(
                remainingDailyMs = null,
                remainingSessionMs = sessionAllowed?.remainingSessionMs,
                remainingHourlyMs = null,
                remainingWeeklyMs = null,
            )
        }

        val appUseRemainingMs = sessionState?.periodAppUse
            ?.remainingMs(usage, periodStarts, limit)
            ?.takeIf { it > 0L }
        val wallClockGraceMs = graceRemainingMs(
            pauses = pauses,
            profileId = profileId,
            packageName = packageName,
            nowEpochMs = nowEpochMs,
            sharedPool = sharedPool,
        )
        val extraRemainingMs = appUseRemainingMs ?: wallClockGraceMs
        val limitResult = LimitEvaluator.evaluate(
            limit = limit,
            usage = usage,
            extensionBonus = limitExtensionBonus,
            graceRemainingMs = extraRemainingMs,
        )
        return when (limitResult) {
            is LimitEvaluator.LimitCheckResult.Blocked -> applyLimitAction(
                config = config,
                reason = limitResult.reason,
                nowEpochMs = nowEpochMs,
                existingBreakUntilEpochMs = sessionState?.breakUntilEpochMs,
            )
            is LimitEvaluator.LimitCheckResult.Allowed -> RuleResult.Allowed(
                remainingDailyMs = limitResult.remainingDailyMs,
                remainingSessionMs = sessionAllowed?.remainingSessionMs,
                remainingHourlyMs = limitResult.remainingHourlyMs,
                remainingWeeklyMs = limitResult.remainingWeeklyMs,
                warningLevel = limitResult.warningLevel,
            )
        }
    }

    private fun evaluateOpenAxis(
        config: ProfileEnforcementConfig,
        profile: Profile,
    ): RuleResult? = evaluateOnOpen(config, profile)

    private fun SessionTracker.SessionCheckResult.toRuleResult(
        config: ProfileEnforcementConfig,
    ): RuleResult? = when (this) {
        is SessionTracker.SessionCheckResult.OnBreak -> applySessionLimitAction(
            config = config,
            reason = BlockReason.onBreak,
            breakUntilEpochMs = breakUntilEpochMs,
        )
        is SessionTracker.SessionCheckResult.SessionExceeded -> applySessionLimitAction(
            config = config,
            reason = BlockReason.sessionLimit,
            breakUntilEpochMs = breakUntilEpochMs,
        )
        is SessionTracker.SessionCheckResult.Allowed -> RuleResult.Allowed(
            remainingDailyMs = null,
            remainingSessionMs = remainingSessionMs,
            remainingHourlyMs = null,
            remainingWeeklyMs = null,
        )
    }

    private fun pickUsageWinner(
        session: RuleResult?,
        period: RuleResult?,
        config: ProfileEnforcementConfig,
    ): RuleResult? {
        val candidates = listOfNotNull(session, period)
        if (candidates.isEmpty()) return null

        return candidates.maxBy { candidate ->
            when (candidate) {
                is RuleResult.Blocked -> blockedActionStrictness(candidate, config)
                is RuleResult.Allowed -> if (candidate.notifyLimitReached) 0 else -1
                else -> -1
            }
        }
    }

    private fun blockedActionStrictness(
        blocked: RuleResult.Blocked,
        config: ProfileEnforcementConfig,
    ): Int = when (blocked.reason) {
        BlockReason.sessionLimit, BlockReason.onBreak -> sessionActionStrictness(config.onSessionLimitAction)
        BlockReason.dailyLimit, BlockReason.hourlyLimit, BlockReason.weeklyLimit ->
            limitActionStrictness(config.onLimitAction)
        else -> 5
    }

    private fun suppressesOpenGate(
        blocked: RuleResult.Blocked,
        config: ProfileEnforcementConfig,
    ): Boolean {
        val strictness = blockedActionStrictness(blocked, config)
        return strictness >= limitActionStrictness(OnLimitAction.mandatoryBreak)
    }

    private fun openResultStrictness(result: RuleResult): Int = when (result) {
        is RuleResult.DelayOpen -> 1
        is RuleResult.OpenDeterrent -> when (result.method) {
            FrictionMethod.waitOneMin -> 1
            FrictionMethod.math, FrictionMethod.password -> 2
            else -> 2
        }
        else -> -1
    }

    private fun mergeAllowedResults(
        session: RuleResult?,
        period: RuleResult?,
        usageWinner: RuleResult.Allowed,
    ): RuleResult.Allowed {
        val sessionAllowed = session as? RuleResult.Allowed
        val periodAllowed = period as? RuleResult.Allowed
        val notifyLimitReached = (sessionAllowed?.notifyLimitReached == true) ||
            (periodAllowed?.notifyLimitReached == true)
        val notifyReason = when {
            periodAllowed?.notifyLimitReached == true -> periodAllowed.notifyLimitReason
            sessionAllowed?.notifyLimitReached == true -> sessionAllowed.notifyLimitReason
            else -> null
        }
        return RuleResult.Allowed(
            remainingDailyMs = periodAllowed?.remainingDailyMs ?: usageWinner.remainingDailyMs,
            remainingSessionMs = sessionAllowed?.remainingSessionMs ?: usageWinner.remainingSessionMs,
            remainingHourlyMs = periodAllowed?.remainingHourlyMs ?: usageWinner.remainingHourlyMs,
            remainingWeeklyMs = periodAllowed?.remainingWeeklyMs ?: usageWinner.remainingWeeklyMs,
            warningLevel = periodAllowed?.warningLevel ?: usageWinner.warningLevel,
            notifyLimitReached = notifyLimitReached,
            notifyLimitReason = notifyReason,
        )
    }

    private fun applyLimitAction(
        config: ProfileEnforcementConfig,
        reason: BlockReason,
        nowEpochMs: Long,
        existingBreakUntilEpochMs: Long?,
    ): RuleResult {
        val breakUntil = PeriodContinuePolicy.periodBreakUntilEpochMs(
            existingBreakUntilEpochMs = existingBreakUntilEpochMs,
            nowEpochMs = nowEpochMs,
            breakDurationMs = config.limitBreakDurationMs,
        )
        return when (config.onLimitAction) {
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
            OnLimitAction.deterrentMath -> RuleResult.Blocked(
                reason = reason,
                bypassAllowed = true,
                sessionDeterrent = FrictionMethod.math,
            )
            OnLimitAction.deterrentWait -> RuleResult.Blocked(
                reason = reason,
                bypassAllowed = true,
                sessionDeterrent = FrictionMethod.waitOneMin,
            )
            OnLimitAction.mandatoryBreak -> RuleResult.Blocked(
                reason = reason,
                breakUntilEpochMs = breakUntil,
                bypassAllowed = false,
            )
            OnLimitAction.hardBlock -> RuleResult.Blocked(
                reason = reason,
                bypassAllowed = false,
            )
        }
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
            bypassAllowed = true,
            sessionDeterrent = FrictionMethod.math,
        )
        OnSessionLimitAction.deterrentWait -> RuleResult.Blocked(
            reason = reason,
            bypassAllowed = true,
            sessionDeterrent = FrictionMethod.waitOneMin,
        )
        OnSessionLimitAction.limitWithExtensions -> RuleResult.Blocked(
            reason = reason,
            bypassAllowed = true,
        )
        OnSessionLimitAction.mandatoryBreak -> RuleResult.Blocked(
            reason = reason,
            breakUntilEpochMs = breakUntilEpochMs,
            bypassAllowed = false,
        )
        OnSessionLimitAction.hardBlock -> RuleResult.Blocked(
            reason = reason,
            bypassAllowed = false,
        )
    }

    private fun evaluateOnOpen(
        config: ProfileEnforcementConfig,
        profile: Profile,
    ): RuleResult? = when (config.onOpenAction) {
        OnOpenAction.none -> {
            if (profile.delayOpenSeconds > 0) {
                RuleResult.DelayOpen(profile.delayOpenSeconds)
            } else {
                null
            }
        }
        OnOpenAction.pinGate -> if (!profile.passwordHash.isNullOrBlank()) {
            RuleResult.OpenDeterrent(method = FrictionMethod.password)
        } else {
            null
        }
        OnOpenAction.deterrentMath -> RuleResult.OpenDeterrent(
            method = FrictionMethod.math,
        )
        OnOpenAction.deterrentWait -> RuleResult.OpenDeterrent(
            method = FrictionMethod.waitOneMin,
        )
    }

    private fun limitActionStrictness(action: OnLimitAction): Int = when (action) {
        OnLimitAction.notifyOnly -> 0
        OnLimitAction.limitWithExtensions -> 1
        OnLimitAction.deterrentWait -> 2
        OnLimitAction.deterrentMath -> 3
        OnLimitAction.mandatoryBreak -> 4
        OnLimitAction.hardBlock -> 5
    }

    private fun sessionActionStrictness(action: OnSessionLimitAction): Int = when (action) {
        OnSessionLimitAction.notifyOnly -> 0
        OnSessionLimitAction.limitWithExtensions -> 1
        OnSessionLimitAction.deterrentWait -> 2
        OnSessionLimitAction.deterrentMath -> 3
        OnSessionLimitAction.mandatoryBreak -> 4
        OnSessionLimitAction.hardBlock -> 5
    }
}
