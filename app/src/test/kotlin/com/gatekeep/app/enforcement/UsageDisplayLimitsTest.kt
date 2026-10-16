package com.gatekeep.app.enforcement

import com.gatekeep.app.util.formatDurationMinutes
import com.gatekeep.domain.ExtensionPeriodDisplay
import com.gatekeep.domain.PauseManager
import com.gatekeep.domain.PeriodDuration
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageDisplayLimitsTest {

    @Test
    fun `hud and current usage share the same display limit resolver`() {
        val extension = ExtensionPeriodDisplay(bonusMs = 15 * 60_000L, anchorMs = 65 * 60_000L)
        assertEquals(
            80 * 60_000L,
            UsageDisplayLimits.displayLimitMs(
                baseLimitMs = 60 * 60_000L,
                extension = extension,
                noLimitToday = false,
                periodMs = PeriodDuration.dayMs,
            ),
        )
    }

    @Test
    fun `persisted no limit today pause yields null display limit and infinity line`() {
        val now = 1_000_000L
        val pause = Pause(
            profileId = 1L,
            packageName = "com.gatekeep.app",
            type = PauseType.noLimitToday,
            untilEpochMs = now + 60_000L,
        )
        assertTrue(
            PauseManager.isNoLimitTodayActive(
                pauses = listOf(pause),
                profileId = 1L,
                packageName = "com.gatekeep.app",
                nowEpochMs = now,
                sharedPool = false,
            ),
        )
        val displayLimitMs = UsageDisplayLimits.displayLimitMs(
            baseLimitMs = 60_000L,
            extension = ExtensionPeriodDisplay(bonusMs = 0L, anchorMs = 0L),
            noLimitToday = true,
            periodMs = PeriodDuration.dayMs,
        )
        assertNull(displayLimitMs)
        assertEquals(
            "1m / ∞",
            "${formatDurationMinutes(60_000L)} / ${formatDurationMinutes(displayLimitMs)}",
        )
    }
}
