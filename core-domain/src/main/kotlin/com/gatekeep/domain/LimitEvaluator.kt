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
    ): LimitCheckResult {
        if (limit == null || !limit.enabled) {
            return LimitCheckResult.Allowed(
                remainingDailyMs = null,
                remainingHourlyMs = null,
                remainingWeeklyMs = null,
                warningLevel = WarningLevel.none,
            )
        }

        val dailyRemaining = limit.dailyLimitMs?.let { it + extensionBonus.dailyMs - usage.dailyMs }
        val hourlyRemaining = limit.hourlyLimitMs?.let { it + extensionBonus.hourlyMs - usage.hourlyMs }
        val weeklyRemaining = limit.weeklyLimitMs?.let { it + extensionBonus.weeklyMs - usage.weeklyMs }

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
            isNearLimit(limit.dailyLimitMs, usage.dailyMs) ||
                isNearLimit(limit.hourlyLimitMs, usage.hourlyMs) ||
                isNearLimit(limit.weeklyLimitMs, usage.weeklyMs) -> WarningLevel.eightyPercent
            else -> WarningLevel.none
        }

        return LimitCheckResult.Allowed(
            remainingDailyMs = dailyRemaining,
            remainingHourlyMs = hourlyRemaining,
            remainingWeeklyMs = weeklyRemaining,
            warningLevel = warning,
        )
    }

    private fun isNearLimit(limitMs: Long?, usedMs: Long): Boolean {
        if (limitMs == null || limitMs <= 0) return false
        return usedMs.toDouble() / limitMs >= 0.8
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
