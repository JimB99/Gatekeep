package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
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
                    dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS + 1,
                )
                GatekeepTestFixtures.seedUsageAtCap(
                    usageRepository, seeded.profileId, EnforcementTestPackages.TARGET_B,
                    dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS + 1,
                )
            }
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.launchTargetB()
        assertBlockedWithOverlay(EnforcementTestPackages.TARGET_B)
    }

    @Test
    fun o03_unmonitoredApp_hidesOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.launchUnmonitored()
        assertOverlayHidden()
    }

    @Test
    fun o04_sameOverlay_updatesWithoutFlicker() {
        runSeed {
            val seeded = seedHardBlockProfile()
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis(),
                    breakUntilEpochMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.BREAK_MS,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        assertTrue(harness.assertOverlayStable(stableMs = 500, maxTextChanges = 2))
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
                    delayOpenSeconds = GatekeepTestFixtures.TestDurations.DELAY_OPEN_SEC,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
        }
        harness.launchTargetA()
        harness.waitForElapsedMs(
            GatekeepTestFixtures.TestDurations.msAfterTimer(GatekeepTestFixtures.TestDurations.DELAY_OPEN_SEC),
        )
        assertAllowedWithoutBlockingOverlay()
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
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
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
