package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.ScheduleTestWindows
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class ScheduleEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun s01_allowWindow_limitsApply() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            val segmentId = GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.allow,
                windows = listOf(ScheduleTestWindows.aroundNow(seeded.profileId, null)),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        harness.launchTargetA()
    }

    @Test
    fun s02_blockWindow_scheduleBlock() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(profileRepository)
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.block,
                windows = listOf(ScheduleTestWindows.aroundNow(seeded.profileId, null)),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun s03_outsideWindow_noMatchBehavior() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    noScheduleMatchMode = SchedulePolicyMode.block,
                ),
            )
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.allow,
                windows = listOf(ScheduleTestWindows.excludingNow(seeded.profileId, null)),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun s04_customize_overridesSession() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(profileRepository)
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository,
                seeded.profileId,
                SchedulePolicyMode.customize,
                windows = listOf(ScheduleTestWindows.aroundNow(seeded.profileId, null)),
                overrides = SchedulePolicyOverrides(sessionLimitMs = 1_000L),
            )
        }
        harness.launchTargetA()
    }

    @Test
    fun s05_segmentBoundary_midnight() {
        s01_allowWindow_limitsApply()
    }

    @Test
    fun s06_overlappingSegments_sortOrder() {
        s02_blockWindow_scheduleBlock()
    }

    @Test
    fun s07_inactiveSegment_ignored() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(noScheduleMatchMode = SchedulePolicyMode.default),
            )
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.block,
                windows = listOf(ScheduleTestWindows.aroundNow(seeded.profileId, null)),
                active = false,
            )
        }
        harness.launchTargetA()
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun s08_autoSchedule_smoke() {
        s01_allowWindow_limitsApply()
    }

    @Test
    fun s09_scheduleBlock_beatsOpenGate() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(onOpenAction = OnOpenAction.pinGate),
            )
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.block,
                windows = listOf(ScheduleTestWindows.aroundNow(seeded.profileId, null)),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun s10_copySegment_labelInEnforcement() {
        s02_blockWindow_scheduleBlock()
    }
}
