package com.gatekeep.app.ui.stats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsPeriodicRefreshPolicyTest {

    @Test
    fun shouldRefresh_whenAtCurrentPeriod() {
        assertTrue(StatsPeriodicRefreshPolicy.shouldRefreshPeriodically(viewingCurrentPeriod = true))
    }

    @Test
    fun shouldNotRefresh_whenViewingPastPeriod() {
        assertFalse(StatsPeriodicRefreshPolicy.shouldRefreshPeriodically(viewingCurrentPeriod = false))
    }
}
