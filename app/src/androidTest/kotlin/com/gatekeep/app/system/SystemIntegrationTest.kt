package com.gatekeep.app.system

import androidx.test.filters.LargeTest
import com.gatekeep.app.enforcement.EnforcementCrossAppTestBase
import org.junit.Assert.assertTrue
import org.junit.Test

@LargeTest
class SystemIntegrationTest : EnforcementCrossAppTestBase() {

    @Test
    fun sys01_launcherTargetHomeRecents() {
        seedHardBlockProfile()
        harness.launchTargetA()
        harness.pressHome()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun sys02_settingsForeground_noCrash() {
        seedHardBlockProfile()
        harness.launchTargetB()
        harness.sleepMs(2_000)
    }

    @Test
    fun sys03_splitScreen_optional() {
        seedHardBlockProfile()
        harness.launchTargetA()
    }
}
