package com.gatekeep.app.ui.pause

import com.gatekeep.domain.TimeBoundaries
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseDurationModelsTest {

    @Test
    fun isPauseUntilEndOfDay_matchesDayBoundary() {
        val now = 1_700_000_000_000L
        val dayEnd = TimeBoundaries.dayBounds(now).endExclusiveMs
        assertTrue(isPauseUntilEndOfDay(dayEnd, now))
        assertFalse(isPauseUntilEndOfDay(dayEnd - 60_000L, now))
    }

    @Test
    fun resolveActiveDurationChoice_untilDayEnd_returnsToday() {
        val now = 1_700_000_000_000L
        val dayEnd = TimeBoundaries.dayBounds(now).endExclusiveMs
        val pause = Pause(
            profileId = 1,
            packageName = null,
            type = PauseType.untilDatetime,
            untilEpochMs = dayEnd,
        )
        assertEquals(DurationChoice.Today, resolveActiveDurationChoice(pause, now))
    }

    @Test
    fun resolveActiveDurationChoice_noLimitToday_returnsNull() {
        val pause = Pause(
            profileId = 1,
            packageName = "com.test.app",
            type = PauseType.noLimitToday,
            untilEpochMs = Long.MAX_VALUE,
        )
        assertNull(resolveActiveDurationChoice(pause, System.currentTimeMillis()))
    }

    @Test
    fun isAllowPauseDisplayType_excludesPeriodOnlyPauses() {
        assertFalse(isAllowPauseDisplayType(PauseType.noLimitToday))
        assertFalse(isAllowPauseDisplayType(PauseType.extensionGrace))
        assertTrue(isAllowPauseDisplayType(PauseType.fifteenMin))
    }
}
