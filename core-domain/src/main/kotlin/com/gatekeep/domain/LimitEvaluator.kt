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
            graceRemainingMs,
            PeriodDuration.dayMs,
        )
        val hourlyRemaining = remainingForPeriod(
            limit.hourlyLimitMs,
            usage.hourlyMs,
            extensionBonus.hourlyMs,
            graceRemainingMs,
            PeriodDuration.hourMs,
        )
        val weeklyRemaining = remainingForPeriod(
            limit.weeklyLimitMs,
            usage.weeklyMs,
            extensionBonus.weeklyMs,
            graceRemainingMs,
            PeriodDuration.weekMs,
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
                graceRemainingMs,
                PeriodDuration.dayMs,
            ) ||
                isNearLimit(
                    limit.hourlyLimitMs,
                    usage.hourlyMs,
                    extensionBonus.hourlyMs,
                    graceRemainingMs,
                    PeriodDuration.hourMs,
                ) ||
                isNearLimit(
                    limit.weeklyLimitMs,
                    usage.weeklyMs,
                    extensionBonus.weeklyMs,
                    graceRemainingMs,
                    PeriodDuration.weekMs,
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

    private fun remainingForPeriod(
        limitMs: Long?,
        usedMs: Long,
        bonusMs: Long,
        graceRemainingMs: Long?,
        periodMs: Long,
    ): Long? {
        val effective = effectiveCap(limitMs, usedMs, bonusMs, graceRemainingMs, periodMs) ?: return null
        return effective - usedMs
    }

    private fun isNearLimit(
        limitMs: Long?,
        usedMs: Long,
        bonusMs: Long,
        graceRemainingMs: Long?,
        periodMs: Long,
    ): Boolean {
        val effectiveLimit = effectiveCap(limitMs, usedMs, bonusMs, graceRemainingMs, periodMs) ?: return false
        if (effectiveLimit <= 0) return false
        return usedMs.toDouble() / effectiveLimit >= 0.8
    }

    private fun effectiveCap(
        limitMs: Long?,
        usedMs: Long,
        bonusMs: Long,
        graceRemainingMs: Long?,
        periodMs: Long,
    ): Long? = EffectiveLimitDisplay.effectiveLimitMs(
        baseLimitMs = limitMs,
        usageMs = usedMs,
        extensionBonusMs = bonusMs,
        graceRemainingMs = graceRemainingMs,
        noLimitToday = false,
        periodMs = periodMs,
    )

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
