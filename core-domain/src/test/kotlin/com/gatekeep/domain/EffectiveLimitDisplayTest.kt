package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EffectiveLimitDisplayTest {

    @Test
    fun `grace cap uses usage plus remaining when over base bonus`() {
        val effective = EffectiveLimitDisplay.effectiveLimitMs(
            baseLimitMs = 60 * 60_000L,
            usageMs = 70 * 60_000L,
            extensionBonusMs = 5 * 60_000L,
            graceRemainingMs = 15 * 60_000L,
            noLimitToday = false,
        )
        assertEquals(85 * 60_000L, effective)
    }

    @Test
    fun `no limit today returns null cap`() {
        assertNull(
            EffectiveLimitDisplay.effectiveLimitMs(
                baseLimitMs = 60 * 60_000L,
                usageMs = 30 * 60_000L,
                extensionBonusMs = 0L,
                graceRemainingMs = null,
                noLimitToday = true,
            ),
        )
    }

    @Test
    fun `bonus cap when no grace`() {
        assertEquals(
            75 * 60_000L,
            EffectiveLimitDisplay.effectiveLimitMs(
                baseLimitMs = 60 * 60_000L,
                usageMs = 50 * 60_000L,
                extensionBonusMs = 15 * 60_000L,
                graceRemainingMs = null,
                noLimitToday = false,
            ),
        )
    }

    @Test
    fun `grace cap uses usage plus remaining even when under base bonus`() {
        val effective = EffectiveLimitDisplay.effectiveLimitMs(
            baseLimitMs = 60 * 60_000L,
            usageMs = 40 * 60_000L,
            extensionBonusMs = 15 * 60_000L,
            graceRemainingMs = 15 * 60_000L,
            noLimitToday = false,
        )
        assertEquals(55 * 60_000L, effective)
    }
}
