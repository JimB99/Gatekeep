package com.gatekeep.app.enforcement

import com.gatekeep.domain.EnforcementPollInterval

class CountdownController {
    data class Deadlines(
        val sessionMs: Long? = null,
        val dailyMs: Long? = null,
        val hourlyMs: Long? = null,
        val weeklyMs: Long? = null,
    )

    fun pollIntervalMs(nowEpochMs: Long, deadlines: Deadlines): Long? =
        EnforcementPollInterval.enforcementLoopIntervalMs(
            nowEpochMs,
            listOf(deadlines.sessionMs, deadlines.dailyMs, deadlines.hourlyMs, deadlines.weeklyMs),
        )
}
