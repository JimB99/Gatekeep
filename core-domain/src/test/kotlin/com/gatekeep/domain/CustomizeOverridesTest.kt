package com.gatekeep.domain

import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SchedulePolicyOverrides
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomizeOverridesTest {

    private val profile = Profile(
        id = 1,
        name = "Test",
        isActive = true,
        weeklyLimitMs = 7 * 60 * 60_000L,
        dailyLimitMs = 2 * 60 * 60_000L,
        hourlyLimitMs = 30 * 60_000L,
        sessionLimitMs = 15 * 60_000L,
    )

    @Test
    fun `hasAnyLimitValue is false when all limits null`() {
        assertFalse(CustomizeOverrides.hasAnyLimitValue(SchedulePolicyOverrides()))
    }

    @Test
    fun `hasAnyLimitValue is true when any limit set including zero`() {
        assertTrue(
            CustomizeOverrides.hasAnyLimitValue(
                SchedulePolicyOverrides(dailyLimitMs = 0L),
            ),
        )
    }

    @Test
    fun `resolveForEditor pre-fills from profile when never configured`() {
        val resolved = CustomizeOverrides.resolveForEditor(profile, SchedulePolicyOverrides())
        assertEquals(profile.weeklyLimitMs, resolved.weeklyLimitMs)
        assertEquals(profile.dailyLimitMs, resolved.dailyLimitMs)
        assertEquals(profile.hourlyLimitMs, resolved.hourlyLimitMs)
        assertEquals(profile.sessionLimitMs, resolved.sessionLimitMs)
    }

    @Test
    fun `resolveForEditor keeps explicit zero for unset tiers`() {
        val overrides = SchedulePolicyOverrides(dailyLimitMs = 45 * 60_000L)
        val resolved = CustomizeOverrides.resolveForEditor(profile, overrides)
        assertEquals(0L, resolved.weeklyLimitMs)
        assertEquals(45 * 60_000L, resolved.dailyLimitMs)
        assertEquals(0L, resolved.hourlyLimitMs)
        assertEquals(0L, resolved.sessionLimitMs)
    }

    @Test
    fun `apply does not inherit profile limits for unset customize tiers`() {
        val overrides = SchedulePolicyOverrides(dailyLimitMs = 30 * 60_000L)
        val applied = CustomizeOverrides.apply(profile, overrides)
        assertNull(applied.weeklyLimitMs)
        assertEquals(30 * 60_000L, applied.dailyLimitMs)
        assertNull(applied.hourlyLimitMs)
        assertNull(applied.sessionLimitMs)
    }

    @Test
    fun `apply with only onLimitAction leaves daily off so RuleEngine allows at profile cap`() {
        val applied = CustomizeOverrides.apply(
            profile,
            SchedulePolicyOverrides(onLimitAction = OnLimitAction.hardBlock),
        )
        assertNull(applied.dailyLimitMs)
        val result = RuleEngine.evaluate(
            RuleEvaluationContext(
                nowEpochMs = 1_000L,
                packageName = "com.test",
                profile = applied,
                limit = applied.toAppLimit("com.test"),
                isMonitored = true,
                usage = UsageSnapshot(dailyMs = profile.dailyLimitMs!!),
                sessionState = null,
                pauses = emptyList(),
                enforcementConfig = applied.enforcementConfig(),
            ),
        )
        assertTrue(result is RuleResult.Allowed)
    }

    @Test
    fun `apply treats zero as off`() {
        val overrides = SchedulePolicyOverrides(
            weeklyLimitMs = 0L,
            dailyLimitMs = 60 * 60_000L,
        )
        val applied = CustomizeOverrides.apply(profile, overrides)
        assertNull(applied.weeklyLimitMs)
        assertEquals(60 * 60_000L, applied.dailyLimitMs)
    }
}
