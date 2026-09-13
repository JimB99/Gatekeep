package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDestinationsTest {

    @Test
    fun dashboard_mapsToRoute() {
        assertEquals("dashboard", NotificationDestination.Dashboard.toRoute())
    }

    @Test
    fun stats_mapsToRoute() {
        assertEquals("stats", NotificationDestination.Stats.toRoute())
    }

    @Test
    fun profileCurrentUsage_mapsToRoute() {
        assertEquals(
            "profile/42/current-usage",
            NotificationDestination.ProfileCurrentUsage(42L).toRoute(),
        )
    }
}
