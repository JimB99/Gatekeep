package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.ScheduleTestWindows
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.SchedulePolicyMode
import org.junit.Assert.assertTrue
import org.junit.Test

@LargeTest
class PauseEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun pa02_appOnlyPause_otherAppsLimited() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                extraPackages = listOf(EnforcementTestPackages.TARGET_B to EnforcementTestPackages.TARGET_B_LABEL),
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedUsageAtCap(usageRepository, seeded.profileId, EnforcementTestPackages.TARGET_A, dailyMs = 60 * 60_000L)
            GatekeepTestFixtures.seedPause(usageRepository, PauseType.fiveMin, seeded.profileId, EnforcementTestPackages.TARGET_A)
        }
        harness.launchTargetB()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun pa03_multiProfile_selectivePause() {
        runSeed {
            val a = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(name = "A"),
            )
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(name = "B"),
            )
            GatekeepTestFixtures.seedPause(usageRepository, PauseType.fifteenMin, a.profileId)
        }
        harness.launchTargetA()
    }

    @Test
    fun pa05_noLimitToday_perApp() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedPause(
                usageRepository, PauseType.noLimitToday, seeded.profileId, seeded.packageName,
                untilMs = System.currentTimeMillis() + 60 * 60_000L,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlayGone(timeoutMs = 5_000) || !harness.waitForOverlay(2_000))
    }

    @Test
    fun pa06_noLimitToday_sharedPool() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    limitUsageScope = LimitUsageScope.sharedPool,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedPause(
                usageRepository, PauseType.noLimitToday, seeded.profileId, packageName = null,
                untilMs = System.currentTimeMillis() + 60 * 60_000L,
            )
        }
        harness.launchTargetA()
    }

    @Test
    fun pa07_pauseExpires_whileInForeground() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedUsageAtCap(usageRepository, seeded.profileId, seeded.packageName, dailyMs = 60 * 60_000L)
            GatekeepTestFixtures.seedPause(
                usageRepository, PauseType.fiveMin, seeded.profileId,
                untilMs = System.currentTimeMillis() + 1_000L,
            )
        }
        harness.launchTargetA()
        harness.sleepMs(2_000)
        enforcementCoordinator.refresh()
    }

    @Test
    fun pa08_pauseOverrides_scheduleBlock() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            val segmentId = GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.block,
                windows = listOf(ScheduleTestWindows.aroundNow(seeded.profileId, null)),
            )
            GatekeepTestFixtures.seedPause(usageRepository, PauseType.sixtyMin, seeded.profileId)
        }
        harness.launchTargetA()
    }

    @Test
    fun pa09_focusMode_blocks() {
        runSeed {
            settingsRepository.updateSettings {
                it.copy(focusModeUntilMs = System.currentTimeMillis() + 60 * 60_000L)
            }
            GatekeepTestFixtures.seedProfileWithMonitoredApp(profileRepository = profileRepository)
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun pa10_focusBlock_profileScoped() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(name = "Focused"),
            )
            usageRepository.addFocusBlock(seeded.profileId, System.currentTimeMillis() + 60 * 60_000L, System.currentTimeMillis())
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun pa12_overlappingPauses() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(profileRepository)
            GatekeepTestFixtures.seedPause(usageRepository, PauseType.fiveMin, seeded.profileId)
            GatekeepTestFixtures.seedPause(usageRepository, PauseType.sixtyMin, seeded.profileId)
        }
        harness.launchTargetA()
    }
}
