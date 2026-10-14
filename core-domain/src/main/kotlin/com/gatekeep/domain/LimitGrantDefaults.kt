package com.gatekeep.domain

import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction

/**
 * Defaults for session refill and period continue.
 * Enforcement may ignore these fields until later tickets; storage and rules UI use them now.
 */
object LimitGrantDefaults {
    const val DEFAULT_PERIOD_CONTINUE_MS = 15L * 60_000L

    fun hasSessionCap(sessionLimitMs: Long?): Boolean =
        sessionLimitMs != null && sessionLimitMs > 0

    /** Unresolved continue follows the session cap when one exists, otherwise 15 min. */
    fun defaultContinueInherit(sessionLimitMs: Long? = null): Boolean = true

    /**
     * Persist the inherit *intent*, not the chip's selected appearance.
     * The chip is off when there is no session cap, but default continue still
     * follows a session cap added later until the user picks custom.
     */
    fun continueInheritToPersist(inheritIntent: Boolean): Boolean = inheritIntent

    /** Chip appearance only. Do not use this value when saving. */
    fun inheritChipSelected(inherit: Boolean, sessionLimitMs: Long?): Boolean =
        inherit && hasSessionCap(sessionLimitMs)

    fun seedCustomContinueMs(sessionLimitMs: Long?): Long =
        sessionLimitMs?.takeIf { it > 0 } ?: DEFAULT_PERIOD_CONTINUE_MS

    fun showsPeriodContinue(action: OnLimitAction): Boolean = when (action) {
        OnLimitAction.deterrentWait,
        OnLimitAction.deterrentMath,
        OnLimitAction.mandatoryBreak,
        -> true
        OnLimitAction.notifyOnly,
        OnLimitAction.limitWithExtensions,
        OnLimitAction.hardBlock,
        -> false
    }

    fun showsSessionRefill(action: OnSessionLimitAction): Boolean = when (action) {
        OnSessionLimitAction.deterrentWait,
        OnSessionLimitAction.deterrentMath,
        -> true
        OnSessionLimitAction.notifyOnly,
        OnSessionLimitAction.limitWithExtensions,
        OnSessionLimitAction.mandatoryBreak,
        OnSessionLimitAction.hardBlock,
        -> false
    }

    /** Upgrade copies the shared math difficulty onto every axis. Never writes a 5-minute grant. */
    fun migrateAxisDifficulties(shared: FrictionDifficulty): AxisDifficulties =
        AxisDifficulties(open = shared, period = shared, session = shared)

    fun migrateSessionRefillOverrideMs(): Long? = null

    fun migratePeriodContinueInherit(sessionLimitMs: Long? = null): Boolean =
        defaultContinueInherit(sessionLimitMs)

    fun migratePeriodContinueDurationMs(): Long = DEFAULT_PERIOD_CONTINUE_MS
}

data class AxisDifficulties(
    val open: FrictionDifficulty,
    val period: FrictionDifficulty,
    val session: FrictionDifficulty,
)
