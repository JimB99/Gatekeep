package com.gatekeep.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPeriodicRefreshRulesTest {

    @Test
    fun shouldSchedule_whenEnabled() {
        assertTrue(ForegroundPeriodicRefreshRules.shouldSchedule(enabled = true))
        assertFalse(ForegroundPeriodicRefreshRules.shouldSchedule(enabled = false))
    }

    @Test
    fun coercedIntervalMs_enforcesMinimum() {
        assertEquals(100L, ForegroundPeriodicRefreshRules.coercedIntervalMs(0L))
        assertEquals(1_000L, ForegroundPeriodicRefreshRules.coercedIntervalMs(1_000L))
    }
}
