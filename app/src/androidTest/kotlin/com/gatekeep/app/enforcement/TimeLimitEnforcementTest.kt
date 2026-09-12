package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.PauseType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class TimeLimitEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun tl01_sessionLimit_triggersAction() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    onSessionLimitAction = OnSessionLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis() - GatekeepTestFixtures.TestDurations.SESSION_OVER_CAP_MS,
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
                    dailyLimitMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS + 1,
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
                    hourlyLimitMs = GatekeepTestFixtures.TestDurations.HOURLY_LIMIT_MS,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                hourlyMs = GatekeepTestFixtures.TestDurations.HOURLY_LIMIT_MS + 1_000L,
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
                    weeklyLimitMs = GatekeepTestFixtures.TestDurations.WEEKLY_LIMIT_MS,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                weeklyMs = GatekeepTestFixtures.TestDurations.WEEKLY_LIMIT_MS + 1,
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
                    dailyLimitMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
                    limitUsageScope = LimitUsageScope.sharedPool,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                EnforcementTestPackages.TARGET_A,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS + 1,
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
                    dailyLimitMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
                    onLimitAction = OnLimitAction.limitWithExtensions,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
            usageRepository.logOverride(
                seeded.packageName,
                seeded.profileId,
                com.gatekeep.domain.model.OverrideMethod.extension,
                GatekeepTestFixtures.TestDurations.EXTENSION_BONUS_MS,
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
                untilMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.FUTURE_OFFSET_MS,
            )
        }
        harness.launchTargetA()
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun tl09_gradualTightening_reducesLimit() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = GatekeepTestFixtures.TestDurations.GRADUAL_TIGHTENING_LIMIT_MS,
                    gradualTighteningEnabled = true,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.GRADUAL_TIGHTENING_LIMIT_MS + 1,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
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
        assertAllowedWithoutBlockingOverlay()
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
        assertOverlayHidden()
    }

    @Test
    fun tl_emptyMonitored_notEnforced() {
        runSeed {
            val id = profileRepository.createProfile("Empty")
            profileRepository.toggleProfileActive(id, true)
        }
        harness.launchTargetA()
        assertOverlayHidden()
    }
}
