package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.PeriodAppUseBudget
import com.gatekeep.domain.model.PeriodStarts
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.SessionState
import com.gatekeep.domain.model.UsageSnapshot

/**
 * Period wait/math/break continue and wait/break-length-0 silent auto-continue.
 * Independent of overlay extension quota and [ExtensionRequestEvaluator].
 */
object PeriodContinuePolicy {

    fun continueMs(config: ProfileEnforcementConfig, sessionLimitMs: Long?): Long =
        LimitGrantDefaults.periodContinueMs(
            sessionLimitMs = sessionLimitMs,
            inheritSessionCap = config.periodContinueInheritSessionCap,
            customMs = config.periodContinueDurationMs,
        )

    fun shouldSilentAutoContinue(config: ProfileEnforcementConfig): Boolean =
        when (config.onLimitAction) {
            OnLimitAction.deterrentWait -> config.limitWaitDurationSeconds <= 0
            OnLimitAction.mandatoryBreak -> (config.limitBreakDurationMs ?: 0L) <= 0L
            OnLimitAction.deterrentMath,
            OnLimitAction.notifyOnly,
            OnLimitAction.limitWithExtensions,
            OnLimitAction.hardBlock,
            -> false
        }

    /**
     * Future period-break deadline is kept (leave does not skip it). A past deadline
     * is not reused; a new break starts from now when continue has run out.
     */
    fun periodBreakUntilEpochMs(
        existingBreakUntilEpochMs: Long?,
        nowEpochMs: Long,
        breakDurationMs: Long?,
    ): Long? {
        if (breakDurationMs == null || breakDurationMs <= 0L) return null
        if (existingBreakUntilEpochMs != null && existingBreakUntilEpochMs > nowEpochMs) {
            return existingBreakUntilEpochMs
        }
        return nowEpochMs + breakDurationMs
    }

    fun applyContinue(
        session: SessionState,
        usage: UsageSnapshot,
        continueMs: Long,
        periodStarts: PeriodStarts,
        stack: Boolean = false,
        markFrictionPaid: Boolean = true,
    ): SessionState {
        val granted = session.periodAppUse.grant(
            usage = usage,
            extraMs = continueMs,
            starts = periodStarts,
            stack = stack,
        )
        val cleared = SessionTracker.clearPendingWait(
            SessionTracker.clearBreak(session.withPeriodAppUse(granted)),
        )
        return if (markFrictionPaid) SessionTracker.markLimitFrictionPaid(cleared) else cleared
    }

    fun applyExtendExtras(
        session: SessionState,
        usage: UsageSnapshot,
        extraMs: Long,
        periodStarts: PeriodStarts,
    ): SessionState {
        val granted = session.periodAppUse.grant(
            usage = usage,
            extraMs = extraMs,
            starts = periodStarts,
            stack = true,
        )
        return SessionTracker.clearBreak(session.withPeriodAppUse(granted))
    }

    /**
     * When a period mandatory break has ended, grant continue once and clear the deadline
     * so evaluate cannot re-apply a past [SessionState.breakUntilEpochMs].
     */
    fun completeExpiredPeriodBreak(
        session: SessionState,
        usage: UsageSnapshot,
        continueMs: Long,
        periodStarts: PeriodStarts,
        nowEpochMs: Long,
    ): SessionState {
        val breakUntil = session.breakUntilEpochMs ?: return session
        if (nowEpochMs < breakUntil) return session
        return applyContinue(session, usage, continueMs, periodStarts)
    }

    /**
     * Wait or break length 0: grant continue whenever the period cap is hit, with no overlay.
     */
    fun applySilentContinueIfNeeded(
        session: SessionState,
        limit: AppLimit?,
        usage: UsageSnapshot,
        config: ProfileEnforcementConfig,
        periodStarts: PeriodStarts,
    ): SessionState? {
        if (limit == null || !limit.enabled) return null
        if (!shouldSilentAutoContinue(config)) return null
        val continueMs = continueMs(config, limit.sessionLimitMs)
        if (continueMs <= 0L) return null
        if (session.periodAppUse.remainingMs(usage, periodStarts, limit) > 0L) return null
        return when (LimitEvaluator.evaluate(limit, usage)) {
            is LimitEvaluator.LimitCheckResult.Blocked ->
                applyContinue(
                    session,
                    usage,
                    continueMs,
                    periodStarts,
                    markFrictionPaid = false,
                )
            is LimitEvaluator.LimitCheckResult.Allowed -> null
        }
    }
}

fun SessionState.withPeriodAppUse(budget: PeriodAppUseBudget): SessionState =
    copy(periodAppUse = budget)
