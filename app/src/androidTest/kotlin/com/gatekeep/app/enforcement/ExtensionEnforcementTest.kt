package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.PauseType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class ExtensionEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun e01_grantFromOverlay_extendsSession() {
        runSeed {
            seedBlockedProfile(
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    limitExtensionPolicy = ExtensionPolicy(surfaceMode = ExtensionSurfaceMode.overlay),
                ),
            )
        }
        harness.launchTargetA()
        assertTrue("e01 overlay", harness.waitForOverlay())
        assertTrue(
            "e01 +5 click and overlay gone ${harness.screenDiagnostics()}",
            harness.clickOverlayExtensionMinutes(5),
        )
        assertOverlayHidden()
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun e03_maxPerDay_denied() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    limitExtensionPolicy = ExtensionPolicy(maxExtensionsPerDay = 0),
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
    fun e04_maxConsecutive_denied() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    limitExtensionPolicy = ExtensionPolicy(maxConsecutiveExtensions = 0),
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun e05_noLimitToday_override() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.limitWithExtensions),
            )
            GatekeepTestFixtures.seedPause(
                usageRepository, PauseType.noLimitToday, seeded.profileId, seeded.packageName,
                untilMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.FUTURE_OFFSET_MS,
            )
        }
        harness.launchTargetA()
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun e06_leftoverGracePause_doesNotAllow_extrasDo() {
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = seedBlockedProfile(
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                ),
            )
            usageRepository.addExtensionGracePause(
                seeded.profileId, seeded.packageName,
                System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.EXTENSION_GRACE_MS,
                System.currentTimeMillis(),
            )
        }
        harness.launchTargetA()
        assertTrue("e06 overlay", harness.waitForOverlay())
        val granted = runBlocking {
            enforcementCoordinator.grantExtensionForProfileAwait(
                seeded.profileId,
                seeded.packageName,
                minutes = 5,
            )
        }
        assertTrue(granted)
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun sessionWait_grantSessionRefill_allowsOverCap() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onLimitAction = OnLimitAction.notifyOnly,
                    onSessionLimitAction = OnSessionLimitAction.deterrentWait,
                    dailyLimitMs = null,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    sessionWaitDurationSeconds = 30,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = GatekeepTestFixtures.continuedSession(
                    seeded.packageName,
                    GatekeepTestFixtures.TestDurations.SESSION_OVER_CAP_MS,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(
            "session wait overlay ${harness.screenDiagnostics()}",
            harness.waitForOverlay(),
        )
        assertTrue(
            "grantSessionRefill",
            runBlocking { enforcementCoordinator.grantSessionRefillAwait(EnforcementTestPackages.TARGET_A) },
        )
        assertAllowedWithoutBlockingOverlay()
        assertOverlayHidden()
    }

    @Test
    fun periodWait_grantPeriodContinue_allowsOverCap() {
        runSeed {
            seedBlockedProfile(
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onLimitAction = OnLimitAction.deterrentWait,
                    onSessionLimitAction = OnSessionLimitAction.notifyOnly,
                    sessionLimitMs = null,
                    limitWaitDurationSeconds = 30,
                ),
                dailyMs = GatekeepTestFixtures.TestDurations.OVER_DAILY_CAP_MS,
            )
        }
        harness.launchTargetA()
        assertTrue(
            "period wait overlay ${harness.screenDiagnostics()}",
            harness.waitForOverlay(),
        )
        assertTrue(
            "grantPeriodContinue",
            runBlocking { enforcementCoordinator.grantPeriodContinueAwait(EnforcementTestPackages.TARGET_A) },
        )
        assertAllowedWithoutBlockingOverlay()
        assertOverlayHidden()
    }

    @Test
    fun e07_surfaceOverlayOnly() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    limitExtensionPolicy = ExtensionPolicy(surfaceMode = ExtensionSurfaceMode.overlay),
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue("e07 overlay", harness.waitForOverlay())
    }

    @Test
    fun e08_customMinutes_extension() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    limitExtensionPolicy = ExtensionPolicy(customEnabled = true, customMinutes = 7),
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue("e08 overlay", harness.waitForOverlay())
    }

    @Test
    fun e09_afterHardBlock_denied() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        assertTrue(!harness.isExtensionButtonsVisible())
    }
}
