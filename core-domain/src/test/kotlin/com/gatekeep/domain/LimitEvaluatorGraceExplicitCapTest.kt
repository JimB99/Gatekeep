package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.LimitExtensionBonus
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Documents extensionGrace enforcement (usage + grace) vs explicit HUD cap.
 * Production grants do not create extensionGrace today; see ExtensionGrantEngine.planGrant.
 */
class LimitEvaluatorGraceExplicitCapTest {

    @Test
    fun `extension grace allows usage above explicit display cap during grace window`() {
        val explicitCapMs = 85 * 60_000L
        val usageMs = explicitCapMs + 2 * 60_000L
        val graceRemainingMs = 10 * 60_000L
        val limit = AppLimit(1, "com.test", dailyLimitMs = 65 * 60_000L, enabled = true)
        val result = LimitEvaluator.evaluate(
            limit,
            UsageSnapshot(dailyMs = usageMs),
            LimitExtensionBonus(
                dailyMs = 20 * 60_000L,
                dailyMaxExplicitCapMs = explicitCapMs,
            ),
            graceRemainingMs = graceRemainingMs,
        )
        assertTrue(result is LimitEvaluator.LimitCheckResult.Allowed)
        assertEquals(
            graceRemainingMs,
            (result as LimitEvaluator.LimitCheckResult.Allowed).remainingDailyMs,
        )
    }
}
