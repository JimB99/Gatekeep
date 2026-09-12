package com.gatekeep.domain

object EffectiveLimitDisplay {

    /**
     * User-facing cap for HUD and Current Usage.
     * Without extension bonus: always [baseLimitMs], even when usage exceeds it.
     * With bonus below base: base + bonus (fixed).
     * With bonus at/above base: [extensionAnchorMs] + bonus (anchor snapshotted at grant, minute-rounded).
     */
    fun displayLimitMs(
        baseLimitMs: Long?,
        extensionBonusMs: Long,
        extensionAnchorMs: Long,
        noLimitToday: Boolean,
        periodMs: Long,
    ): Long? {
        if (baseLimitMs == null) return null
        if (noLimitToday) return null
        if (extensionBonusMs <= 0L) {
            return PeriodDuration.unlimitedIfAtLeastPeriod(baseLimitMs, periodMs)
        }
        val anchorMs = extensionAnchorMs.takeIf { it >= baseLimitMs } ?: 0L
        val cap = if (anchorMs > 0L) anchorMs + extensionBonusMs else baseLimitMs + extensionBonusMs
        return PeriodDuration.unlimitedIfAtLeastPeriod(cap, periodMs)
    }

    /**
     * Effective cap for enforcement.
     * With an active grace, the cap is current usage + remaining grace (counts from now).
     * Otherwise it is base + persisted extension bonuses.
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
        if (graceCap != null) {
            return graceCap
        }
        val cap = baseLimitMs + extensionBonusMs
        return PeriodDuration.unlimitedIfAtLeastPeriod(cap, periodMs)
    }
}
