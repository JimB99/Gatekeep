package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.LimitExtensionBonus
import com.gatekeep.domain.model.UsageSnapshot
import com.gatekeep.domain.model.WarningLevel

object LimitEvaluator {

    fun evaluate(
        limit: AppLimit?,
        usage: UsageSnapshot,
        extensionBonus: LimitExtensionBonus = LimitExtensionBonus(),
        graceRemainingMs: Long? = null,
    ): LimitCheckResult {
        if (limit == null || !limit.enabled) {
            return LimitCheckResult.Allowed(
                remainingDailyMs = null,
                remainingHourlyMs = null,
                remainingWeeklyMs = null,
                warningLevel = WarningLevel.none,
            )
        }

        val dailyRemaining = remainingForPeriod(
            limit.dailyLimitMs,
            usage.dailyMs,
            extensionBonus.dailyMs,
            extensionBonus.dailyAnchorMs,
            graceRemainingMs,
            PeriodDuration.dayMs,
            useDisplayCap = true,
        )
        val hourlyRemaining = remainingForPeriod(
            limit.hourlyLimitMs,
            usage.hourlyMs,
            extensionBonus.hourlyMs,
            0L,
            graceRemainingMs,
            PeriodDuration.hourMs,
            useDisplayCap = false,
        )
        val weeklyRemaining = remainingForPeriod(
            limit.weeklyLimitMs,
            usage.weeklyMs,
            extensionBonus.weeklyMs,
            0L,
            graceRemainingMs,
            PeriodDuration.weekMs,
            useDisplayCap = false,
        )

        if (dailyRemaining != null && dailyRemaining <= 0) {
            return LimitCheckResult.Blocked(com.gatekeep.domain.model.BlockReason.dailyLimit)
        }
        if (hourlyRemaining != null && hourlyRemaining <= 0) {
            return LimitCheckResult.Blocked(com.gatekeep.domain.model.BlockReason.hourlyLimit)
        }
        if (weeklyRemaining != null && weeklyRemaining <= 0) {
            return LimitCheckResult.Blocked(com.gatekeep.domain.model.BlockReason.weeklyLimit)
        }

        val warning = when {
            isNearLimit(
                limit.dailyLimitMs,
                usage.dailyMs,
                extensionBonus.dailyMs,
                extensionBonus.dailyAnchorMs,
                graceRemainingMs,
                PeriodDuration.dayMs,
                useDisplayCap = true,
            ) ||
                isNearLimit(
                    limit.hourlyLimitMs,
                    usage.hourlyMs,
                    extensionBonus.hourlyMs,
                    0L,
                    graceRemainingMs,
                    PeriodDuration.hourMs,
                    useDisplayCap = false,
                ) ||
                isNearLimit(
                    limit.weeklyLimitMs,
                    usage.weeklyMs,
                    extensionBonus.weeklyMs,
                    0L,
                    graceRemainingMs,
                    PeriodDuration.weekMs,
                    useDisplayCap = false,
                ) ->
                WarningLevel.eightyPercent
            else -> WarningLevel.none
        }

        return LimitCheckResult.Allowed(
            remainingDailyMs = dailyRemaining,
            remainingHourlyMs = hourlyRemaining,
            remainingWeeklyMs = weeklyRemaining,
            warningLevel = warning,
        )
    }

    fun dailyCapMs(
        limit: AppLimit?,
        usage: UsageSnapshot,
        extensionBonus: LimitExtensionBonus = LimitExtensionBonus(),
        graceRemainingMs: Long? = null,
        periodLimitsDisabled: Boolean = false,
    ): Long? {
        if (limit == null || !limit.enabled) return null
        return capForPeriod(
            limit.dailyLimitMs,
            usage.dailyMs,
            extensionBonus.dailyMs,
            extensionBonus.dailyAnchorMs,
            graceRemainingMs,
            PeriodDuration.dayMs,
            periodLimitsDisabled,
            useDisplayCap = true,
        )
    }

    private fun remainingForPeriod(
        limitMs: Long?,
        usedMs: Long,
        bonusMs: Long,
        anchorMs: Long,
        graceRemainingMs: Long?,
        periodMs: Long,
        useDisplayCap: Boolean,
    ): Long? {
        val effective = capForPeriod(
            limitMs, usedMs, bonusMs, anchorMs, graceRemainingMs, periodMs,
            periodLimitsDisabled = false,
            useDisplayCap = useDisplayCap,
        ) ?: return null
        return effective - usedMs
    }

    private fun isNearLimit(
        limitMs: Long?,
        usedMs: Long,
        bonusMs: Long,
        anchorMs: Long,
        graceRemainingMs: Long?,
        periodMs: Long,
        useDisplayCap: Boolean,
    ): Boolean {
        val effectiveLimit = capForPeriod(
            limitMs, usedMs, bonusMs, anchorMs, graceRemainingMs, periodMs,
            periodLimitsDisabled = false,
            useDisplayCap = useDisplayCap,
        ) ?: return false
        if (effectiveLimit <= 0) return false
        return usedMs.toDouble() / effectiveLimit >= 0.8
    }

    /**
     * Daily overlay remaining matches Current Usage (display cap = anchor + bonus) when there
     * is no grace. Hourly/weekly keep base + bonus. Active grace is usage + remaining grace.
     */
    private fun capForPeriod(
        limitMs: Long?,
        usedMs: Long,
        bonusMs: Long,
        anchorMs: Long,
        graceRemainingMs: Long?,
        periodMs: Long,
        periodLimitsDisabled: Boolean,
        useDisplayCap: Boolean,
    ): Long? {
        if (periodLimitsDisabled) return null
        if (graceRemainingMs != null && graceRemainingMs > 0L) {
            return EffectiveLimitDisplay.effectiveLimitMs(
                baseLimitMs = limitMs,
                usageMs = usedMs,
                extensionBonusMs = bonusMs,
                graceRemainingMs = graceRemainingMs,
                noLimitToday = false,
                periodMs = periodMs,
            )
        }
        if (useDisplayCap) {
            return EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = limitMs,
                extensionBonusMs = bonusMs,
                extensionAnchorMs = anchorMs,
                noLimitToday = false,
                periodMs = periodMs,
            )
        }
        return EffectiveLimitDisplay.effectiveLimitMs(
            baseLimitMs = limitMs,
            usageMs = usedMs,
            extensionBonusMs = bonusMs,
            graceRemainingMs = null,
            noLimitToday = false,
            periodMs = periodMs,
        )
    }

    sealed class LimitCheckResult {
        data class Allowed(
            val remainingDailyMs: Long?,
            val remainingHourlyMs: Long?,
            val remainingWeeklyMs: Long?,
            val warningLevel: WarningLevel,
        ) : LimitCheckResult()

        data class Blocked(val reason: com.gatekeep.domain.model.BlockReason) : LimitCheckResult()
    }
}
