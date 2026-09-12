package com.gatekeep.app.enforcement

import com.gatekeep.domain.ExtensionPeriodDisplay
import com.gatekeep.domain.PeriodDuration
import org.junit.Assert.assertEquals
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
}
