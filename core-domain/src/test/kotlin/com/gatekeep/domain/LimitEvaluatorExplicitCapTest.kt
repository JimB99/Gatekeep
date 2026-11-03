package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.LimitExtensionBonus
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitEvaluatorExplicitCapTest {

    @Test
    fun `hourly explicit cap blocks at cap`() {
        val explicitCapMs = 55 * 60_000L
        val limit = AppLimit(1, "com.test", hourlyLimitMs = 50 * 60_000L, enabled = true)
        val result = LimitEvaluator.evaluate(
            limit,
            UsageSnapshot(hourlyMs = explicitCapMs),
            LimitExtensionBonus(hourlyMaxExplicitCapMs = explicitCapMs),
        )
        assertTrue(result is LimitEvaluator.LimitCheckResult.Blocked)
        assertEquals(
            BlockReason.hourlyLimit,
            (result as LimitEvaluator.LimitCheckResult.Blocked).reason,
        )
    }

    @Test
    fun `hourly explicit cap allows five minutes under cap`() {
        val explicitCapMs = 55 * 60_000L
        val limit = AppLimit(1, "com.test", hourlyLimitMs = 50 * 60_000L, enabled = true)
        val result = LimitEvaluator.evaluate(
            limit,
            UsageSnapshot(hourlyMs = 50 * 60_000L),
            LimitExtensionBonus(hourlyMaxExplicitCapMs = explicitCapMs),
        )
        assertTrue(result is LimitEvaluator.LimitCheckResult.Allowed)
        assertEquals(
            5 * 60_000L,
            (result as LimitEvaluator.LimitCheckResult.Allowed).remainingHourlyMs,
        )
    }

    @Test
    fun `weekly explicit cap blocks at cap`() {
        val explicitCapMs = 6 * PeriodDuration.dayMs + 12 * PeriodDuration.hourMs
        val limit = AppLimit(1, "com.test", weeklyLimitMs = 5 * PeriodDuration.dayMs, enabled = true)
        val result = LimitEvaluator.evaluate(
            limit,
            UsageSnapshot(weeklyMs = explicitCapMs),
            LimitExtensionBonus(weeklyMaxExplicitCapMs = explicitCapMs),
        )
        assertTrue(result is LimitEvaluator.LimitCheckResult.Blocked)
        assertEquals(
            BlockReason.weeklyLimit,
            (result as LimitEvaluator.LimitCheckResult.Blocked).reason,
        )
    }
}
