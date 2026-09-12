package com.gatekeep.app.system

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gatekeep.app.enforcement.EnforcementCrossAppTestBase
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
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
        harness.waitForElapsedMs(400)
    }

    @Test
    fun sys03_splitScreen_optional() {
        seedHardBlockProfile()
        harness.launchTargetA()
    }
}
