package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.ScheduleTestWindows
import org.junit.Assert.assertTrue
import org.junit.Test

@LargeTest
class MultiProfileEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun mp01_twoProfiles_strictestCap() {
        runSeed {
            val strict = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    name = "Strict",
                    dailyLimitMs = 30 * 60_000L,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            val loose = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    name = "Loose",
                    dailyLimitMs = 120 * 60_000L,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            profileRepository.toggleProfileActive(strict.profileId, true)
            profileRepository.toggleProfileActive(loose.profileId, true)
            profileRepository.addMonitoredApp(
                com.gatekeep.domain.model.MonitoredApp(
                    loose.profileId, EnforcementTestPackages.TARGET_A,
                    EnforcementTestPackages.TARGET_A_LABEL,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, strict.profileId, EnforcementTestPackages.TARGET_A, dailyMs = 31 * 60_000L,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun mp02_onePaused_oneActive() {
        runSeed {
            val paused = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(name = "Paused", onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(name = "Active", onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedPause(usageRepository, PauseType.sixtyMin, paused.profileId)
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, paused.profileId, EnforcementTestPackages.TARGET_A, dailyMs = 60 * 60_000L,
            )
        }
        harness.launchTargetA()
    }

    @Test
    fun mp03_differentSchedules_sameApp() {
        runSeed {
            val a = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(name = "SchedA"),
            )
            val b = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(name = "SchedB"),
            )
            profileRepository.toggleProfileActive(b.profileId, true)
            profileRepository.addMonitoredApp(
                com.gatekeep.domain.model.MonitoredApp(
                    b.profileId, EnforcementTestPackages.TARGET_A, EnforcementTestPackages.TARGET_A_LABEL,
                ),
            )
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, a.profileId, SchedulePolicyMode.block,
                windows = listOf(ScheduleTestWindows.aroundNow(a.profileId, null)),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }
}
