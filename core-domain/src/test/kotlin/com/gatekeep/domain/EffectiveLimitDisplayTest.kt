package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PeriodDurationTest {

    @Test
    fun `at least period is unlimited`() {
        assertNull(PeriodDuration.unlimitedIfAtLeastPeriod(PeriodDuration.hourMs, PeriodDuration.hourMs))
        assertNull(PeriodDuration.unlimitedIfAtLeastPeriod(PeriodDuration.hourMs + 1, PeriodDuration.hourMs))
    }

    @Test
    fun `below period stays finite`() {
        assertEquals(59 * 60_000L, PeriodDuration.unlimitedIfAtLeastPeriod(59 * 60_000L, PeriodDuration.hourMs))
    }
}

class EffectiveLimitDisplayTest {

    @Test
    fun `grace cap stays finite when usage exceeds period length`() {
        val usageMs = 25 * PeriodDuration.hourMs
        val graceMs = 15 * 60_000L
        assertEquals(
            usageMs + graceMs,
            EffectiveLimitDisplay.effectiveLimitMs(
                baseLimitMs = 60 * 60_000L,
                usageMs = usageMs,
                extensionBonusMs = 0L,
                graceRemainingMs = graceMs,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
            ),
        )
    }

    @Test
    fun `grace cap uses usage plus remaining when over base bonus`() {
        val effective = EffectiveLimitDisplay.effectiveLimitMs(
            baseLimitMs = 60 * 60_000L,
            usageMs = 70 * 60_000L,
            extensionBonusMs = 5 * 60_000L,
            graceRemainingMs = 15 * 60_000L,
            noLimitToday = false,
            periodMs = PeriodDuration.dayMs,
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
                periodMs = PeriodDuration.hourMs,
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
                periodMs = PeriodDuration.dayMs,
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
            periodMs = PeriodDuration.dayMs,
        )
        assertEquals(55 * 60_000L, effective)
    }

    @Test
    fun `hourly bonus at or over an hour is unlimited`() {
        assertNull(
            EffectiveLimitDisplay.effectiveLimitMs(
                baseLimitMs = 50 * 60_000L,
                usageMs = 40 * 60_000L,
                extensionBonusMs = 15 * 60_000L,
                graceRemainingMs = null,
                noLimitToday = false,
                periodMs = PeriodDuration.hourMs,
            ),
        )
    }

    @Test
    fun `daily bonus at or over a day is unlimited`() {
        assertNull(
            EffectiveLimitDisplay.effectiveLimitMs(
                baseLimitMs = 20 * PeriodDuration.hourMs,
                usageMs = 5 * PeriodDuration.hourMs,
                extensionBonusMs = 5 * PeriodDuration.hourMs,
                graceRemainingMs = null,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
            ),
        )
    }

    @Test
    fun `weekly bonus at or over a week is unlimited`() {
        assertNull(
            EffectiveLimitDisplay.effectiveLimitMs(
                baseLimitMs = 6 * PeriodDuration.dayMs,
                usageMs = PeriodDuration.dayMs,
                extensionBonusMs = PeriodDuration.dayMs,
                graceRemainingMs = null,
                noLimitToday = false,
                periodMs = PeriodDuration.weekMs,
            ),
        )
    }
}

class EffectiveLimitDisplayHudTest {

    @Test
    fun `display limit is base plus bonus when below base`() {
        assertEquals(
            75 * 60_000L,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = 60 * 60_000L,
                extensionBonusMs = 15 * 60_000L,
                extensionAnchorMs = 0L,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
            ),
        )
    }

    @Test
    fun `display limit stays at base when usage exceeds base without extension`() {
        assertEquals(
            60 * 60_000L,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = 60 * 60_000L,
                extensionBonusMs = 0L,
                extensionAnchorMs = 0L,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
            ),
        )
    }

    @Test
    fun `display limit uses stored anchor plus bonus when over base`() {
        assertEquals(
            80 * 60_000L,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = 60 * 60_000L,
                extensionBonusMs = 15 * 60_000L,
                extensionAnchorMs = 65 * 60_000L,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
            ),
        )
    }

    @Test
    fun `display limit is stable across refreshes without grace remaining`() {
        val first = EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = 60 * 60_000L,
            extensionBonusMs = 15 * 60_000L,
            extensionAnchorMs = 65 * 60_000L,
            noLimitToday = false,
            periodMs = PeriodDuration.dayMs,
        )
        val second = EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = 60 * 60_000L,
            extensionBonusMs = 15 * 60_000L,
            extensionAnchorMs = 65 * 60_000L,
            noLimitToday = false,
            periodMs = PeriodDuration.dayMs,
        )
        assertEquals(first, second)
        assertEquals(80 * 60_000L, first)
    }

    @Test
    fun `display limit null when no limit today`() {
        assertNull(
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = 60 * 60_000L,
                extensionBonusMs = 0L,
                extensionAnchorMs = 0L,
                noLimitToday = true,
                periodMs = PeriodDuration.dayMs,
            ),
        )
    }
}
