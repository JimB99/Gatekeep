package com.gatekeep.domain.model

/**
 * Extra period app-use (continue after wait/math/break, or Limit + extend minutes).
 * Remaining is [until] minus current usage, so leave/lock do not burn it.
 */
data class PeriodStarts(
    val dayStartMs: Long = 0L,
    val hourStartMs: Long = 0L,
    val weekStartMs: Long = 0L,
)

data class PeriodAppUseBudget(
    val untilDailyMs: Long = 0L,
    val untilHourlyMs: Long = 0L,
    val untilWeeklyMs: Long = 0L,
    val dayStartMs: Long = 0L,
    val hourStartMs: Long = 0L,
    val weekStartMs: Long = 0L,
) {
    fun remainingMs(
        usage: UsageSnapshot,
        starts: PeriodStarts,
        limit: AppLimit? = null,
    ): Long {
        var remaining = 0L
        val considerDaily = limit == null || limit.dailyLimitMs != null
        val considerHourly = limit == null || limit.hourlyLimitMs != null
        val considerWeekly = limit == null || limit.weeklyLimitMs != null
        if (considerDaily && dayStartMs == starts.dayStartMs && untilDailyMs > 0L) {
            remaining = maxOf(remaining, untilDailyMs - usage.dailyMs)
        }
        if (considerHourly && hourStartMs == starts.hourStartMs && untilHourlyMs > 0L) {
            remaining = maxOf(remaining, untilHourlyMs - usage.hourlyMs)
        }
        if (considerWeekly && weekStartMs == starts.weekStartMs && untilWeeklyMs > 0L) {
            remaining = maxOf(remaining, untilWeeklyMs - usage.weeklyMs)
        }
        return remaining.coerceAtLeast(0L)
    }

    /**
     * [extraMs] of remaining app-use from current usage (overshoot absorbed).
     * [stack] adds onto leftover remaining; continue grants replace leftover.
     */
    fun grant(
        usage: UsageSnapshot,
        extraMs: Long,
        starts: PeriodStarts,
        stack: Boolean,
    ): PeriodAppUseBudget {
        val extra = extraMs.coerceAtLeast(0L)
        fun nextUntil(currentUntil: Long, usedMs: Long, startMatches: Boolean): Long {
            val leftover = if (startMatches && currentUntil > usedMs) currentUntil - usedMs else 0L
            val add = if (stack) leftover + extra else extra
            return usedMs + add
        }
        return PeriodAppUseBudget(
            untilDailyMs = nextUntil(untilDailyMs, usage.dailyMs, dayStartMs == starts.dayStartMs),
            untilHourlyMs = nextUntil(untilHourlyMs, usage.hourlyMs, hourStartMs == starts.hourStartMs),
            untilWeeklyMs = nextUntil(untilWeeklyMs, usage.weeklyMs, weekStartMs == starts.weekStartMs),
            dayStartMs = starts.dayStartMs,
            hourStartMs = starts.hourStartMs,
            weekStartMs = starts.weekStartMs,
        )
    }
}
