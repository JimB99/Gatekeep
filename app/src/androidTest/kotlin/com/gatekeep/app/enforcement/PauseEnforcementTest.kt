package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.ScheduleTestWindows
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.SchedulePolicyMode
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PauseEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun pa02_appOnlyPause_otherAppsLimited() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                extraPackages = listOf(EnforcementTestPackages.TARGET_B to EnforcementTestPackages.TARGET_B_LABEL),
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                EnforcementTestPackages.TARGET_A,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS + 1,
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                EnforcementTestPackages.TARGET_B,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS + 1,
            )
            GatekeepTestFixtures.seedPause(usageRepository, PauseType.fiveMin, seeded.profileId, EnforcementTestPackages.TARGET_A)
        }
        harness.launchTargetB()
        assertBlockedWithOverlay(EnforcementTestPackages.TARGET_B)
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
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.hardBlock,
                    sessionLimitMs = null,
                ),
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
                untilMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.FUTURE_OFFSET_MS,
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
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
            GatekeepTestFixtures.seedPause(
                usageRepository,
                PauseType.fiveMin,
                seeded.profileId,
                untilMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.PAUSE_EXPIRY_MS,
            )
        }
        harness.launchTargetA()
        harness.waitForElapsedMs(GatekeepTestFixtures.TestDurations.msAfterTimerMs(GatekeepTestFixtures.TestDurations.PAUSE_EXPIRY_MS))
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
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun pa09_focusMode_blocks() {
        runSeed {
            settingsRepository.updateSettings {
                it.copy(focusModeUntilMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.FUTURE_OFFSET_MS)
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
            usageRepository.addFocusBlock(
                seeded.profileId,
                System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.FUTURE_OFFSET_MS,
                System.currentTimeMillis(),
            )
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
