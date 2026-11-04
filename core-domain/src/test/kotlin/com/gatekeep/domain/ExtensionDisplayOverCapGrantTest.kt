package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExtensionDisplayOverCapGrantTest {

    private val hourlyLimitMs = 50 * 60_000L
    private val weeklyLimitMs = 5 * PeriodDuration.dayMs

    private val baseMs = 65 * 60_000L
    private val limit = AppLimit(
        profileId = 1L,
        packageName = "app.test",
        enabled = true,
        dailyLimitMs = baseMs,
    )

    /** Log fixture: dailyUsedMs=4_784_197 (~79m44s), prior cap 4_260_000 (71m). */
    private val usageOverCapMs = 4_784_197L
    private val priorCapMs = 4_260_000L

    @Test
    fun `over cap grant sets explicit cap to ceil usage plus extension not anchor plus full bonus`() {
        val priorBonusMs = 6 * 60_000L
        val prior = ExtensionPeriodDisplay(bonusMs = priorBonusMs, anchorMs = 0L)
        assertEquals(priorCapMs, ExtensionDisplayLogic.legacyDisplayCapMs(baseMs, prior, false, PeriodDuration.dayMs))

        val extensionMs = 5 * 60_000L
        val explicit = ExtensionDisplayLogic.explicitCapsAfterGrant(
            usage = UsageSnapshot(dailyMs = usageOverCapMs),
            limit = limit,
            priorDaily = prior,
            priorHourly = ExtensionPeriodDisplay(0L, 0L),
            priorWeekly = ExtensionPeriodDisplay(0L, 0L),
            extensionMs = extensionMs,
        )
        val expectedCap = ExtensionDisplayLogic.capAfterStackedGrant(priorCapMs, usageOverCapMs, extensionMs)
        assertEquals(expectedCap, explicit.dailyMs)

        val afterDisplay = ExtensionPeriodDisplay(
            bonusMs = priorBonusMs + extensionMs,
            anchorMs = ExtensionDisplayLogic.ceilToMinute(usageOverCapMs),
            maxExplicitCapMs = explicit.dailyMs!!,
        )
        assertEquals(
            expectedCap,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = baseMs,
                extensionBonusMs = afterDisplay.bonusMs,
                extensionAnchorMs = afterDisplay.anchorMs,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
                maxExplicitCapMs = afterDisplay.maxExplicitCapMs,
            ),
        )
        assertEquals(85 * 60_000L, expectedCap)
    }

    @Test
    fun `under cap grant leaves explicit cap null and uses base plus bonus`() {
        val usageMs = 50 * 60_000L
        val prior = ExtensionPeriodDisplay(bonusMs = 5 * 60_000L, anchorMs = 0L)
        val extensionMs = 5 * 60_000L
        val explicit = ExtensionDisplayLogic.explicitCapsAfterGrant(
            usage = UsageSnapshot(dailyMs = usageMs),
            limit = limit,
            priorDaily = prior,
            priorHourly = ExtensionPeriodDisplay(0L, 0L),
            priorWeekly = ExtensionPeriodDisplay(0L, 0L),
            extensionMs = extensionMs,
        )
        assertNull(explicit.dailyMs)
        val display = EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = baseMs,
            extensionBonusMs = prior.bonusMs + extensionMs,
            extensionAnchorMs = 0L,
            noLimitToday = false,
            periodMs = PeriodDuration.dayMs,
        )
        assertEquals(baseMs + 10 * 60_000L, display)
    }

    @Test
    fun `second over cap grant uses max explicit caps across events`() {
        val firstExplicit = 85 * 60_000L
        val prior = ExtensionPeriodDisplay(
            bonusMs = 25 * 60_000L,
            anchorMs = 80 * 60_000L,
            maxExplicitCapMs = firstExplicit,
        )
        val higherUsage = 86 * 60_000L + 30_000L
        val extensionMs = 5 * 60_000L
        val second = ExtensionDisplayLogic.explicitCapsAfterGrant(
            usage = UsageSnapshot(dailyMs = higherUsage),
            limit = limit,
            priorDaily = prior,
            priorHourly = ExtensionPeriodDisplay(0L, 0L),
            priorWeekly = ExtensionPeriodDisplay(0L, 0L),
            extensionMs = extensionMs,
        )
        val priorCap = EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = baseMs,
            extensionBonusMs = prior.bonusMs,
            extensionAnchorMs = prior.anchorMs,
            noLimitToday = false,
            periodMs = PeriodDuration.dayMs,
            maxExplicitCapMs = prior.maxExplicitCapMs,
        )!!
        val secondCap = ExtensionDisplayLogic.capAfterStackedGrant(priorCap, higherUsage, extensionMs)
        assertEquals(secondCap, second.dailyMs)
        assertEquals(
            secondCap,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = baseMs,
                extensionBonusMs = prior.bonusMs + extensionMs,
                extensionAnchorMs = prior.anchorMs,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
                maxExplicitCapMs = secondCap,
            ),
        )
    }

    @Test
    fun `second grant while usage below prior explicit cap stacks on display cap`() {
        val baseDailyMs = 40 * 60_000L
        val priorCapMs = 12_720_000L
        val usageMs = 11_776_964L
        val extensionMs = 15 * 60_000L
        val expectedCapMs = priorCapMs + extensionMs
        val prior = ExtensionPeriodDisplay(
            bonusMs = 39 * 60_000L,
            anchorMs = 11_820_000L,
            maxExplicitCapMs = priorCapMs,
        )
        val explicit = ExtensionDisplayLogic.explicitCapsAfterGrant(
            usage = UsageSnapshot(dailyMs = usageMs),
            limit = limit.copy(dailyLimitMs = baseDailyMs),
            priorDaily = prior,
            priorHourly = ExtensionPeriodDisplay(0L, 0L),
            priorWeekly = ExtensionPeriodDisplay(0L, 0L),
            extensionMs = extensionMs,
        )
        assertEquals(expectedCapMs, explicit.dailyMs)
        val afterDisplay = ExtensionPeriodDisplay(
            bonusMs = prior.bonusMs + extensionMs,
            anchorMs = prior.anchorMs,
            maxExplicitCapMs = explicit.dailyMs!!,
        )
        assertEquals(
            expectedCapMs,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = baseDailyMs,
                extensionBonusMs = afterDisplay.bonusMs,
                extensionAnchorMs = afterDisplay.anchorMs,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
                maxExplicitCapMs = afterDisplay.maxExplicitCapMs,
            ),
        )
    }

    @Test
    fun `stacked grants display uses max explicit cap not lower second grant alone`() {
        val firstCap = 85 * 60_000L
        val secondGrantCap = 82 * 60_000L
        val aggregated = ExtensionDisplayLogic.maxExplicitCapMs(0L, firstCap)
            .let { ExtensionDisplayLogic.maxExplicitCapMs(it, secondGrantCap) }
        assertEquals(firstCap, aggregated)
        assertEquals(
            firstCap,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = baseMs,
                extensionBonusMs = 30 * 60_000L,
                extensionAnchorMs = 80 * 60_000L,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
                maxExplicitCapMs = aggregated,
            ),
        )
        assertEquals(
            secondGrantCap,
            EffectiveLimitDisplay.displayLimitMs(
                baseLimitMs = baseMs,
                extensionBonusMs = 30 * 60_000L,
                extensionAnchorMs = 80 * 60_000L,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
                maxExplicitCapMs = secondGrantCap,
            ),
        )
    }

    @Test
    fun `hourly over cap grant sets explicit cap ceil usage plus extension`() {
        val limit = AppLimit(
            profileId = 1L,
            packageName = "app.test",
            enabled = true,
            hourlyLimitMs = hourlyLimitMs,
        )
        val usageMs = 56 * 60_000L
        val prior = ExtensionPeriodDisplay(bonusMs = 5 * 60_000L, anchorMs = 0L)
        val extensionMs = 5 * 60_000L
        val explicit = ExtensionDisplayLogic.explicitCapsAfterGrant(
            usage = UsageSnapshot(hourlyMs = usageMs),
            limit = limit,
            priorDaily = ExtensionPeriodDisplay(0L, 0L),
            priorHourly = prior,
            priorWeekly = ExtensionPeriodDisplay(0L, 0L),
            extensionMs = extensionMs,
        )
        val priorCap = EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = hourlyLimitMs,
            extensionBonusMs = prior.bonusMs,
            extensionAnchorMs = prior.anchorMs,
            noLimitToday = false,
            periodMs = PeriodDuration.hourMs,
        )!!
        val expected = ExtensionDisplayLogic.capAfterStackedGrant(priorCap, usageMs, extensionMs)
        assertEquals(expected, explicit.hourlyMs)
        assertNull(explicit.dailyMs)
    }

    @Test
    fun `weekly over cap grant sets explicit cap ceil usage plus extension`() {
        val limit = AppLimit(
            profileId = 1L,
            packageName = "app.test",
            enabled = true,
            weeklyLimitMs = weeklyLimitMs,
        )
        val usageMs = weeklyLimitMs + PeriodDuration.dayMs + PeriodDuration.hourMs
        val prior = ExtensionPeriodDisplay(bonusMs = PeriodDuration.dayMs, anchorMs = 0L)
        val extensionMs = 5 * 60_000L
        val explicit = ExtensionDisplayLogic.explicitCapsAfterGrant(
            usage = UsageSnapshot(weeklyMs = usageMs),
            limit = limit,
            priorDaily = ExtensionPeriodDisplay(0L, 0L),
            priorHourly = ExtensionPeriodDisplay(0L, 0L),
            priorWeekly = prior,
            extensionMs = extensionMs,
        )
        val priorCap = EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = weeklyLimitMs,
            extensionBonusMs = prior.bonusMs,
            extensionAnchorMs = prior.anchorMs,
            noLimitToday = false,
            periodMs = PeriodDuration.weekMs,
        )!!
        val expected = ExtensionDisplayLogic.capAfterStackedGrant(priorCap, usageMs, extensionMs)
        assertEquals(expected, explicit.weeklyMs)
        assertNull(explicit.dailyMs)
    }
}
