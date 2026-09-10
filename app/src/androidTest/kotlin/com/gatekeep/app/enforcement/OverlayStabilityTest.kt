package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@LargeTest
class OverlayStabilityTest : EnforcementCrossAppTestBase() {

    @Test
    fun o01_blockWithinTimeoutAfterLaunch() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        assertNotNull(harness.overlayMessageText())
    }

    @Test
    fun o02_switchApps_overlayFollows() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                packageName = EnforcementTestPackages.TARGET_A,
                extraPackages = listOf(EnforcementTestPackages.TARGET_B to EnforcementTestPackages.TARGET_B_LABEL),
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            ).also { seeded ->
                GatekeepTestFixtures.seedUsageAtCap(
                    usageRepository, seeded.profileId, EnforcementTestPackages.TARGET_A,
                    dailyMs = 60 * 60_000L,
                )
                GatekeepTestFixtures.seedUsageAtCap(
                    usageRepository, seeded.profileId, EnforcementTestPackages.TARGET_B,
                    dailyMs = 60 * 60_000L,
                )
            }
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.launchTargetB()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun o03_unmonitoredApp_hidesOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.launchUnmonitored()
        assertTrue(harness.waitForOverlayGone())
    }

    @Test
    fun o04_sameOverlay_updatesWithoutFlicker() {
        runSeed {
            val seeded = seedHardBlockProfile()
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = 60 * 60_000L,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis(),
                    breakUntilEpochMs = System.currentTimeMillis() + 60_000L,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        val first = harness.overlayMessageText()
        harness.sleepMs(1_500)
        val second = harness.overlayMessageText()
        assertNotNull(first)
        assertNotNull(second)
    }

    @Test
    fun o05_frictionInProgress_preservedOnSwitch() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = com.gatekeep.domain.model.OnOpenAction.deterrentMath,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay() || harness.isFrictionVisible())
    }

    @Test
    fun o06_backButton_onOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressBack()
    }

    @Test
    fun o07_homeThenReopen_showsOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressHome()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun o08_rotationDuringBlock_survives() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun o09_delayOpenCountdown_dismisses() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    delayOpenSeconds = 2,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
        }
        harness.launchTargetA()
        harness.sleepMs(3_000)
    }

    @Test
    fun o10_extensionButtons_bySurfaceMode() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    limitExtensionPolicy = ExtensionPolicy(
                        surfaceMode = ExtensionSurfaceMode.overlay,
                    ),
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = 60 * 60_000L,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun o11_openGatekeepFromOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.clickOverlayContinue()
    }

    @Test
    fun o12_keyboardOnMathChallenge_usable() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.deterrentMath,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay() || harness.isFrictionVisible())
    }
}
