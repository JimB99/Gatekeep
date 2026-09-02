package com.gatekeep.domain

object EffectiveLimitDisplay {

    /**
     * Effective cap for UI.
     * With an active grace, the cap is current usage + remaining grace (counts from now).
     * Otherwise it is base + persisted extension bonuses.
     * Returns null when unlimited: no base limit, no limit today, or the cap covers the
     * whole period (hour/day/week).
     */
    fun effectiveLimitMs(
        baseLimitMs: Long?,
        usageMs: Long,
        extensionBonusMs: Long,
        graceRemainingMs: Long?,
        noLimitToday: Boolean,
        periodMs: Long,
    ): Long? {
        if (baseLimitMs == null) return null
        if (noLimitToday) return null
        val graceCap = graceRemainingMs?.let { remaining -> usageMs + remaining }
        val cap = graceCap ?: (baseLimitMs + extensionBonusMs)
        return PeriodDuration.unlimitedIfAtLeastPeriod(cap, periodMs)
    }
}
