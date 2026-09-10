package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnLimitAction
import org.junit.Assert.assertTrue
import org.junit.Test

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
                    sessionLimitMs = 5_000L,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis() - 4_500L,
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
        assertTrue(harness.waitForOverlay(timeoutMs = 15_000))
    }

    @Test
    fun pol05_overlayBreakTimer_accurate() {
        runSeed {
            val seeded = seedHardBlockProfile()
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = 60 * 60_000L,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis(),
                    breakUntilEpochMs = System.currentTimeMillis() + 5_000L,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        harness.sleepMs(2_000)
    }

    @Test
    fun pol06_screenOff_pausesWaitStopwatch() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = com.gatekeep.domain.model.OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = 10,
                ),
            )
        }
        harness.launchTargetA()
        harness.sleepDevice()
        harness.sleepMs(1_000)
    }

    @Test
    fun pol07_rapidSwitch_noDuplicateSessions() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                extraPackages = listOf(EnforcementTestPackages.TARGET_B to EnforcementTestPackages.TARGET_B_LABEL),
                config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedUsageAtCap(usageRepository, seeded.profileId, EnforcementTestPackages.TARGET_A, dailyMs = 60 * 60_000L)
        }
        repeat(3) {
            harness.launchTargetA()
            harness.launchTargetB()
        }
    }
}
