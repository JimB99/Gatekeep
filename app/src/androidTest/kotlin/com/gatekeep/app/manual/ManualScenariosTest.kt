package com.gatekeep.app.manual

import org.junit.Ignore
import org.junit.Test

class ManualScenariosTest {

    @Ignore("Manual: lock device, wake, confirm app PIN screen appears")
    @Test
    fun pin05_screenLockRequiresPinOnWake() {
    }

    @Ignore("Manual: force-stop from Settings, relaunch app, confirm PIN screen")
    @Test
    fun pin06_forceStopRequiresPinOnRelaunch() {
    }

    @Ignore("Manual: verify OEM battery/autostart on Samsung/Xiaomi/OnePlus")
    @Test
    fun man03_oemBatteryAutostart() {
    }

    @Ignore("Manual: verify overlay stability under load on physical device")
    @Test
    fun man04_realDeviceOverlayFlicker() {
    }
}
