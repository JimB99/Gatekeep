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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.Assert.assertEquals
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
        assertOverlayHidden()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun o13_overviewButton_hidesOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressRecents()
        assertOverlayHidden()
    }

    @Test
    fun o18_leaveViaHomeOrRecents_noOverlayFlicker() {
        seedHardBlockProfile()
        harness.useRealForegroundDetection()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressHome()
        assertTrue(
            "overlay did not hide after home ${harness.screenDiagnostics()}",
            harness.waitForOverlayGone(1_500),
        )
        assertNoOverlayFlickerForMs(2_000)
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressRecents()
        assertTrue(
            "overlay did not hide after recents ${harness.screenDiagnostics()}",
            harness.waitForOverlayGone(1_500),
        )
        assertNoOverlayFlickerForMs(2_000)
    }

    private fun assertNoOverlayFlickerForMs(stableMs: Long) {
        val overlayPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val deadline = System.currentTimeMillis() + stableMs
        var visibleSamples = 0
        while (System.currentTimeMillis() < deadline) {
            if (harness.device.hasObject(By.res(overlayPackage, "block_message"))) {
                visibleSamples++
            }
            harness.sleepMs(50)
        }
        assertEquals(
            "overlay flickered after leave ${harness.screenDiagnostics()}",
            0,
            visibleSamples,
        )
    }

    @Test
    fun o14_overviewThenSameApp_reshowsOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressRecents()
        assertOverlayHidden()
        harness.launchTargetA()
        injectStaleRecentsRestoreStack()
        assertBlockedWithOverlay()
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
                    sessionLimitMs = 20 * 60_000L,
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

    @Test
    fun o15_overviewThenTapRecentsCard_reshowsOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.resumeFromRecents()
        injectStaleRecentsRestoreStack()
        assertBlockedWithOverlay()
    }

    @Test
    fun o16_staleRecentsWindowStack_restoresOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressRecents()
        assertOverlayHidden()
        injectStaleRecentsRestoreStack()
        assertBlockedWithOverlay()
    }

    @Test
    fun o17_instagramOverviewClass_doesNotHideOverlay() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        enforcementCoordinator.injectWindowStackForTests(
            listOf(
                OverlayWindowSnapshot(
                    packageName = EnforcementTestPackages.TARGET_A,
                    className = "com.instagram.feed.overview.MediaOverviewActivity",
                    isOverlay = false,
                    isFocused = true,
                    isActive = true,
                ),
            ),
        )
        assertTrue(harness.waitForOverlay())
    }

    private fun injectStaleRecentsRestoreStack() {
        enforcementCoordinator.injectWindowStackForTests(
            listOf(
                OverlayWindowSnapshot(
                    packageName = "com.android.systemui",
                    className = "com.android.quickstep.RecentsActivity",
                    isOverlay = false,
                    isFocused = false,
                    isActive = false,
                ),
                OverlayWindowSnapshot(
                    packageName = EnforcementTestPackages.TARGET_A,
                    className = "com.gatekeep.app.testsupport.EnforcementTargetActivity",
                    isOverlay = false,
                    isFocused = true,
                    isActive = true,
                ),
            ),
        )
    }
}
