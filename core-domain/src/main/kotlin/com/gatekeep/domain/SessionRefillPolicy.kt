package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.SessionState

/**
 * Session wait/math refill and wait/break-length-0 silent auto-refill.
 * Independent of overlay extension quota and [ExtensionRequestEvaluator].
 */
object SessionRefillPolicy {

    fun refillMs(sessionLimitMs: Long?, overrideMs: Long?): Long =
        LimitGrantDefaults.sessionRefillMs(sessionLimitMs, overrideMs)

    fun shouldSilentAutoRefill(config: ProfileEnforcementConfig): Boolean =
        when (config.onSessionLimitAction) {
            OnSessionLimitAction.deterrentWait -> config.sessionWaitDurationSeconds <= 0
            OnSessionLimitAction.mandatoryBreak -> (config.sessionBreakDurationMs ?: 0L) <= 0L
            OnSessionLimitAction.deterrentMath,
            OnSessionLimitAction.notifyOnly,
            OnSessionLimitAction.limitWithExtensions,
            OnSessionLimitAction.hardBlock,
            -> false
        }

    /**
     * When wait or break length is 0 and the sitting is over the session cap, grant the
     * refill amount with no overlay. Non-zero break stays a countdown until [SessionTracker.completeExpiredBreak].
     */
    fun applySilentRefillIfNeeded(
        session: SessionState,
        limit: AppLimit?,
        config: ProfileEnforcementConfig,
        nowEpochMs: Long,
    ): SessionState? {
        if (limit == null || !limit.enabled) return null
        val sessionLimitMs = limit.sessionLimitMs ?: return null
        if (!shouldSilentAutoRefill(config)) return null
        val refillMs = refillMs(sessionLimitMs, config.sessionRefillOverrideMs)
        if (refillMs <= 0L) return null
        return when (SessionTracker.evaluateSession(limit, session, nowEpochMs)) {
            is SessionTracker.SessionCheckResult.SessionExceeded ->
                SessionTracker.applyAppUseRefill(
                    session,
                    sessionLimitMs,
                    refillMs,
                    nowEpochMs,
                    markFrictionPaid = false,
                )
            else -> null
        }
    }
}
