package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.UsageSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtensionDisplayLogicTest {

    @Test
    fun `ceilToMinute rounds up partial minutes`() {
        assertEquals(15 * 60_000L, ExtensionDisplayLogic.ceilToMinute(14 * 60_000L + 21_000L))
        assertEquals(14 * 60_000L, ExtensionDisplayLogic.ceilToMinute(14 * 60_000L))
    }

    @Test
    fun `anchors below base are zero`() {
        val anchors = ExtensionDisplayLogic.anchorsAtGrant(
            usage = UsageSnapshot(dailyMs = 14 * 60_000L + 21_000L),
            limit = AppLimit(
                profileId = 1L,
                packageName = "com.example",
                enabled = true,
                dailyLimitMs = 60 * 60_000L,
            ),
        )
        assertEquals(0L, anchors.dailyMs)
    }

    @Test
    fun `anchors at or above base use minute rounded usage`() {
        val anchors = ExtensionDisplayLogic.anchorsAtGrant(
            usage = UsageSnapshot(dailyMs = 65 * 60_000L + 21_000L),
            limit = AppLimit(
                profileId = 1L,
                packageName = "com.example",
                enabled = true,
                dailyLimitMs = 60 * 60_000L,
            ),
        )
        assertEquals(66 * 60_000L, anchors.dailyMs)
    }

    @Test
    fun `grant display cap uses anchor plus bonus when over base`() {
        val anchors = ExtensionDisplayLogic.anchorsAtGrant(
            usage = UsageSnapshot(dailyMs = 14 * 60_000L + 21_000L),
            limit = AppLimit(
                profileId = 1L,
                packageName = "com.example",
                enabled = true,
                dailyLimitMs = 10 * 60_000L,
            ),
        )
        val cap = EffectiveLimitDisplay.displayLimitMs(
            baseLimitMs = 10 * 60_000L,
            extensionBonusMs = 15 * 60_000L,
            extensionAnchorMs = anchors.dailyMs,
            noLimitToday = false,
            periodMs = PeriodDuration.dayMs,
        )
        assertEquals(30 * 60_000L, cap)
    }
}
