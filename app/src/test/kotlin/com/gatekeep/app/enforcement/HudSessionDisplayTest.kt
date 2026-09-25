package com.gatekeep.app.enforcement

import com.gatekeep.domain.EnforcementPollInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudSessionDisplayTest {

    @Test
    fun `fine poll keeps full precision with seconds`() {
        val display = sessionHudDisplay(90_000L, EnforcementPollInterval.FINE_INTERVAL_MS)
        assertEquals(90_000L, display.displayMs)
        assertTrue(display.includeSeconds)
    }

    @Test
    fun `coarse poll floors to whole minute without seconds`() {
        val remainingMs = 45 * 60_000L + 23_000L
        val display = sessionHudDisplay(remainingMs, EnforcementPollInterval.COARSE_INTERVAL_MS)
        assertEquals(45 * 60_000L, display.displayMs)
        assertFalse(display.includeSeconds)
    }

    @Test
    fun `coarse poll keeps exact minute boundary`() {
        val display = sessionHudDisplay(45 * 60_000L, EnforcementPollInterval.COARSE_INTERVAL_MS)
        assertEquals(45 * 60_000L, display.displayMs)
        assertFalse(display.includeSeconds)
    }
}
