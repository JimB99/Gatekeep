package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.UsageSnapshot

data class ExtensionDisplayAnchors(
    val dailyMs: Long = 0L,
    val hourlyMs: Long = 0L,
    val weeklyMs: Long = 0L,
)

data class ExtensionPeriodDisplay(
    val bonusMs: Long,
    val anchorMs: Long,
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

    private fun anchorForPeriod(usageMs: Long, baseLimitMs: Long?): Long {
        if (baseLimitMs == null) return 0L
        val ceilUsage = ceilToMinute(usageMs)
        return if (ceilUsage >= baseLimitMs) ceilUsage else 0L
    }
}
