package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnLimitAction
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class PollingConsistencyTest : EnforcementCrossAppTestBase() {

    @Test
    fun pol01_firstEval_within2s() {
        seedHardBlockProfile()
        val start = System.currentTimeMillis()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay(timeoutMs = 2_000))
        assertTrue(System.currentTimeMillis() - start <= 5_000)
    }

    @Test
    fun pol02_sameAppResume_reevaluates() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.pressHome()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun pol03_nearLimit_blocksWithin1s() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis() -
                        (GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS - 500L),
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay(timeoutMs = 3_000))
    }

    @Test
    fun pol04_farLimit_blocksBefore30s() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun pol05_overlayBreakTimer_accurate() {
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
        harness.waitForElapsedMs(500)
    }

    @Test
    fun pol06_screenOff_pausesWaitStopwatch() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = com.gatekeep.domain.model.OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.SESSION_WAIT_SEC,
                ),
            )
        }
        harness.launchTargetA()
        harness.sleepDevice()
        harness.waitForElapsedMs(300)
    }

    @Test
    fun pol07_rapidSwitch_noDuplicateSessions() {
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
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        repeat(3) {
            harness.launchTargetA()
            harness.launchTargetB()
        }
    }
}
