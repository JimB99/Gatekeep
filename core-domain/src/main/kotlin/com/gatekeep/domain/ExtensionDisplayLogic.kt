package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.UsageSnapshot

data class ExtensionDisplayAnchors(
    val dailyMs: Long = 0L,
    val hourlyMs: Long = 0L,
    val weeklyMs: Long = 0L,
)

/**
 * Aggregated extension state for one period. [maxExplicitCapMs] is the MAX of the matching
 * `*DisplayCapAfterMs` column on override_events for that period (0 when no over-cap grants).
 */
data class ExtensionPeriodDisplay(
    val bonusMs: Long,
    val anchorMs: Long,
    val maxExplicitCapMs: Long = 0L,
)

data class ExtensionDisplayCapAfter(
    val dailyMs: Long? = null,
    val hourlyMs: Long? = null,
    val weeklyMs: Long? = null,
)

data class ExtensionOverrideLogResult(
    val displayCapBeforeMs: Long?,
    val displayCapAfterMs: Long?,
    val dailyBonusSumMs: Long,
    val dailyAnchorMs: Long,
    val dailyMaxExplicitCapMs: Long,
)

object ExtensionDisplayLogic {

    fun ceilToMinute(ms: Long): Long {
        if (ms <= 0L) return 0L
        return ((ms + 59_999) / 60_000) * 60_000
    }

    fun anchorsAtGrant(usage: UsageSnapshot, limit: AppLimit): ExtensionDisplayAnchors =
        ExtensionDisplayAnchors(
            dailyMs = anchorForPeriod(usage.dailyMs, limit.dailyLimitMs),
            hourlyMs = anchorForPeriod(usage.hourlyMs, limit.hourlyLimitMs),
            weeklyMs = anchorForPeriod(usage.weeklyMs, limit.weeklyLimitMs),
        )

    fun legacyDisplayCapMs(
        baseLimitMs: Long?,
        extension: ExtensionPeriodDisplay,
        noLimitToday: Boolean,
        periodMs: Long,
    ): Long? = EffectiveLimitDisplay.displayLimitMs(
        baseLimitMs = baseLimitMs,
        extensionBonusMs = extension.bonusMs,
        extensionAnchorMs = extension.anchorMs,
        noLimitToday = noLimitToday,
        periodMs = periodMs,
        maxExplicitCapMs = 0L,
    )

    fun isOverDisplayCap(usageMs: Long, displayCapMs: Long?): Boolean =
        displayCapMs != null && usageMs > displayCapMs

    fun capAfterOverCapGrant(usageMs: Long, extensionMs: Long): Long =
        ceilToMinute(usageMs) + extensionMs

    /** Matches SQL MAX(dailyDisplayCapAfterMs) after each grant row is inserted. */
    fun maxExplicitCapMs(existingMaxMs: Long, newGrantCapMs: Long?): Long =
        maxOf(existingMaxMs, newGrantCapMs ?: 0L)

    fun dailyDisplayCapMs(
        baseDailyLimitMs: Long?,
        extension: ExtensionPeriodDisplay,
        noLimitToday: Boolean = false,
    ): Long? {
        if (baseDailyLimitMs == null) return null
        return EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = baseDailyLimitMs,
            extensionBonusMs = extension.bonusMs,
            extensionAnchorMs = extension.anchorMs,
            noLimitToday = noLimitToday,
            periodMs = PeriodDuration.dayMs,
            maxExplicitCapMs = extension.maxExplicitCapMs,
        )
    }

    fun explicitCapsAfterGrant(
        usage: UsageSnapshot,
        limit: AppLimit,
        priorDaily: ExtensionPeriodDisplay,
        priorHourly: ExtensionPeriodDisplay,
        priorWeekly: ExtensionPeriodDisplay,
        extensionMs: Long,
        noLimitToday: Boolean = false,
    ): ExtensionDisplayCapAfter {
        fun forPeriod(
            usageMs: Long,
            baseLimitMs: Long?,
            prior: ExtensionPeriodDisplay,
            periodMs: Long,
        ): Long? {
            if (baseLimitMs == null) return null
            val priorCap = EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = baseLimitMs,
                extensionBonusMs = prior.bonusMs,
                extensionAnchorMs = prior.anchorMs,
                noLimitToday = noLimitToday,
                periodMs = periodMs,
                maxExplicitCapMs = prior.maxExplicitCapMs,
            ) ?: return null
            return if (isOverDisplayCap(usageMs, priorCap)) {
                capAfterOverCapGrant(usageMs, extensionMs)
            } else {
                null
            }
        }
        return ExtensionDisplayCapAfter(
            dailyMs = forPeriod(usage.dailyMs, limit.dailyLimitMs, priorDaily, PeriodDuration.dayMs),
            hourlyMs = forPeriod(usage.hourlyMs, limit.hourlyLimitMs, priorHourly, PeriodDuration.hourMs),
            weeklyMs = forPeriod(usage.weeklyMs, limit.weeklyLimitMs, priorWeekly, PeriodDuration.weekMs),
        )
    }

    private fun anchorForPeriod(usageMs: Long, baseLimitMs: Long?): Long {
        if (baseLimitMs == null) return 0L
        val ceilUsage = ceilToMinute(usageMs)
        return if (ceilUsage >= baseLimitMs) ceilUsage else 0L
    }
}
