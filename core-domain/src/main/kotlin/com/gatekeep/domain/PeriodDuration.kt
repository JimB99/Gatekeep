package com.gatekeep.domain

object PeriodDuration {
    const val hourMs = 60 * 60_000L
    const val dayMs = 24 * hourMs
    const val weekMs = 7 * dayMs

    fun unlimitedIfAtLeastPeriod(effectiveMs: Long, periodMs: Long): Long? =
        if (effectiveMs >= periodMs) null else effectiveMs
}
