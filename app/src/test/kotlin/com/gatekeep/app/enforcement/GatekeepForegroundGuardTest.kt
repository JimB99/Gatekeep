package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM regression for overlay title → Gatekeep; device: `OverlayStabilityTest.o11_openGatekeepFromOverlay`. */
class GatekeepForegroundGuardTest {

    private val gatekeep = "com.gatekeep.app"
    private val blocked = "com.google.android.googlequicksearchbox"

    @Test
    fun suppressesStaleBlockedAppWhileGatekeepCommitted() {
        assertTrue(
            GatekeepForegroundGuard.shouldSuppressThirdPartyForegroundRoute(
                gatekeepPackage = gatekeep,
                currentForegroundPackage = gatekeep,
                incomingPackage = blocked,
            ),
        )
    }

    @Test
    fun allowsGatekeepEventsWhileGatekeepCommitted() {
        assertFalse(
            GatekeepForegroundGuard.shouldSuppressThirdPartyForegroundRoute(
                gatekeepPackage = gatekeep,
                currentForegroundPackage = gatekeep,
                incomingPackage = gatekeep,
            ),
        )
    }

    @Test
    fun allowsLauncherLeaveWhileGatekeepCommitted() {
        assertFalse(
            GatekeepForegroundGuard.shouldSuppressThirdPartyForegroundRoute(
                gatekeepPackage = gatekeep,
                currentForegroundPackage = gatekeep,
                incomingPackage = "com.sec.android.app.launcher",
            ),
        )
    }

    @Test
    fun allowsBlockedAppWhenNotOnGatekeep() {
        assertFalse(
            GatekeepForegroundGuard.shouldSuppressThirdPartyForegroundRoute(
                gatekeepPackage = gatekeep,
                currentForegroundPackage = blocked,
                incomingPackage = blocked,
            ),
        )
    }
}
