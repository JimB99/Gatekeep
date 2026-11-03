package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction
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

    /**
     * Break deadlines after [SessionStartDecision] StartFresh or overlay ticker completion.
     *
     * StartFresh drops an expired [SessionState.breakUntilEpochMs] before enforcement can
     * grant period continue; read [expiredBreakDeadline] from the persisted row (or
     * [persistedBreakFallback]) when the in-memory session no longer carries the field.
     *
     * [hadExpiredBreakDeadline] / [sessionForExpiredBreakCompletion] / [resolveAfterExpiredBreakDeadline]
     * share that contract for evaluate-on-reopen and break-ticker paths.
     */
    fun expiredBreakDeadline(
        sessionState: SessionState,
        persistedBeforeSessionStart: SessionState?,
    ): Long? = sessionState.breakUntilEpochMs ?: persistedBeforeSessionStart?.breakUntilEpochMs

    fun hadExpiredBreakDeadline(
        sessionState: SessionState,
        persistedBeforeSessionStart: SessionState?,
        nowEpochMs: Long,
    ): Boolean = expiredBreakDeadline(sessionState, persistedBeforeSessionStart)
        ?.let { nowEpochMs >= it } == true

    /** When Room lost [SessionState.breakUntilEpochMs], rebuild a prior snapshot for completion. */
    fun persistedBreakFallback(
        sessionState: SessionState,
        fallbackBreakUntilEpochMs: Long?,
    ): SessionState? {
        if (sessionState.breakUntilEpochMs != null || fallbackBreakUntilEpochMs == null) return null
        return sessionState.copy(breakUntilEpochMs = fallbackBreakUntilEpochMs)
    }

    fun sessionForExpiredBreakCompletion(
        sessionState: SessionState,
        persistedBeforeSessionStart: SessionState?,
    ): SessionState {
        val deadline = expiredBreakDeadline(sessionState, persistedBeforeSessionStart)
            ?: return sessionState
        return if (sessionState.breakUntilEpochMs != null) {
            sessionState
        } else {
            sessionState.copy(breakUntilEpochMs = deadline)
        }
    }

    data class ExpiredBreakResolution(
        val session: SessionState,
        val persistPeriodAppUse: Boolean,
    )

    /**
     * Same resolution as enforcement evaluate after StartFresh: complete expired break,
     * optional session sitting refresh, then period continue when at cap with mandatory break.
     * Returns null when nothing changed.
     */
    fun resolveAfterExpiredBreakDeadline(
        sessionState: SessionState,
        persistedBeforeSessionStart: SessionState?,
        nowEpochMs: Long,
        limit: AppLimit?,
        config: ProfileEnforcementConfig,
        usage: UsageSnapshot,
        periodStarts: PeriodStarts,
    ): ExpiredBreakResolution? {
        if (!hadExpiredBreakDeadline(sessionState, persistedBeforeSessionStart, nowEpochMs)) {
            return null
        }
        val sessionWithBreak = sessionForExpiredBreakCompletion(
            sessionState,
            persistedBeforeSessionStart,
        )
        val sessionWithoutBreak = SessionTracker.clearBreak(sessionWithBreak)
        val sessionExceeded = SessionTracker.evaluateSession(limit, sessionWithoutBreak, nowEpochMs) is
            SessionTracker.SessionCheckResult.SessionExceeded
        var resolved = if (
            config.onSessionLimitAction == OnSessionLimitAction.mandatoryBreak && sessionExceeded
        ) {
            SessionTracker.completeExpiredBreak(sessionWithBreak, nowEpochMs)
        } else {
            sessionWithoutBreak
        }
        val periodOver = LimitEvaluator.evaluate(limit, usage) is LimitEvaluator.LimitCheckResult.Blocked
        val needsPeriodContinue = config.onLimitAction == OnLimitAction.mandatoryBreak &&
            periodOver &&
            resolved.periodAppUse.remainingMs(usage, periodStarts, limit) <= 0L
        if (needsPeriodContinue) {
            resolved = applyContinue(
                session = resolved,
                usage = usage,
                continueMs = continueMs(config, limit?.sessionLimitMs),
                periodStarts = periodStarts,
            )
        }
        if (resolved == sessionState) return null
        return ExpiredBreakResolution(resolved, needsPeriodContinue)
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
