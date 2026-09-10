package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.PauseType
import org.junit.Assert.assertTrue
import org.junit.Test

@LargeTest
class ExtensionEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun e01_grantFromOverlay_extendsSession() {
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
        assertTrue(harness.waitForOverlay())
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
            GatekeepTestFixtures.seedUsageAtCap(usageRepository, seeded.profileId, seeded.packageName, dailyMs = 60 * 60_000L)
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
                untilMs = System.currentTimeMillis() + 60 * 60_000L,
            )
        }
        harness.launchTargetA()
    }

    @Test
    fun e06_extensionGrace_pause() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(profileRepository)
            usageRepository.addExtensionGracePause(
                seeded.profileId, seeded.packageName,
                System.currentTimeMillis() + 60_000L, System.currentTimeMillis(),
            )
        }
        harness.launchTargetA()
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
        assertTrue(harness.waitForOverlay())
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
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun e09_afterHardBlock_denied() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        assertTrue(!harness.isExtensionButtonsVisible())
    }
}
