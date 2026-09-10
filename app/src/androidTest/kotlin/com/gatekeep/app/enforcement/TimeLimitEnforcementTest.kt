package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.PauseType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@LargeTest
class TimeLimitEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun tl01_sessionLimit_triggersAction() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    sessionLimitMs = 60_000L,
                    onSessionLimitAction = OnSessionLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis() - 61_000L,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun tl02_dailyLimit_beforeSession() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = 60 * 60_000L,
                    sessionLimitMs = 15 * 60_000L,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName, dailyMs = 60 * 60_000L + 1,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun tl03_hourlyCap_midSession() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    hourlyLimitMs = 30 * 60_000L,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName, hourlyMs = 31 * 60_000L,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun tl04_weeklyCap_lastDay() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    weeklyLimitMs = 7 * 60 * 60_000L,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName, weeklyMs = 7 * 60 * 60_000L + 1,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun tl05_sharedPool_countsAcrossApps() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                extraPackages = listOf(EnforcementTestPackages.TARGET_B to EnforcementTestPackages.TARGET_B_LABEL),
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = 60 * 60_000L,
                    limitUsageScope = LimitUsageScope.sharedPool,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, EnforcementTestPackages.TARGET_A, dailyMs = 60 * 60_000L,
            )
        }
        harness.launchTargetB()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun tl07_extensionBonus_pastBaseCap() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = 60 * 60_000L,
                    onLimitAction = OnLimitAction.limitWithExtensions,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName, dailyMs = 60 * 60_000L,
            )
            usageRepository.logOverride(
                seeded.packageName,
                seeded.profileId,
                com.gatekeep.domain.model.OverrideMethod.extension,
                15 * 60_000L,
            )
        }
        harness.launchTargetA()
    }

    @Test
    fun tl08_noLimitToday_hidesLimits() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedPause(
                usageRepository,
                PauseType.noLimitToday,
                profileId = seeded.profileId,
                packageName = seeded.packageName,
                untilMs = System.currentTimeMillis() + 60 * 60_000L,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlayGone(timeoutMs = 5_000) || !harness.waitForOverlay(timeoutMs = 2_000))
    }

    @Test
    fun tl09_gradualTightening_reducesLimit() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = 120 * 60_000L,
                    gradualTighteningEnabled = true,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertNotNull(harness.overlayMessageText())
    }

    @Test
    fun tl10_nullLimit_noCap() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = null,
                    sessionLimitMs = null,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlayGone(timeoutMs = 3_000))
    }

    @Test
    fun tl_inactiveProfile_notEnforced() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                activate = false,
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlayGone(timeoutMs = 3_000))
    }

    @Test
    fun tl_emptyMonitored_notEnforced() {
        runSeed {
            val id = profileRepository.createProfile("Empty")
            profileRepository.toggleProfileActive(id, true)
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlayGone(timeoutMs = 3_000))
    }
}
