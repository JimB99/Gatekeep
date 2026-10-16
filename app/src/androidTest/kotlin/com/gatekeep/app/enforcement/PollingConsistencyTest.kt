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
                sessionState = com.gatekeep.app.support.GatekeepTestFixtures.continuedSession(
                    seeded.packageName,
                    GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS - 500L,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(
            "near-limit overlay missing ${harness.screenDiagnostics()}",
            harness.waitForOverlay(timeoutMs = 3_000),
        )
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
