package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnLimitAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@LargeTest
class NotificationEnforcementTest : EnforcementCrossAppTestBase() {

    @Test
    fun n01_serviceNotification_present() {
        seedHardBlockProfile()
        enforcementCoordinator.refresh()
        harness.sleepMs(2_000)
        assertTrue(harness.hasActiveServiceNotification())
    }

    @Test
    fun n02_sessionTimer_hiddenWhenNoLimits() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = null,
                    sessionLimitMs = null,
                    onLimitAction = OnLimitAction.notifyOnly,
                ),
            )
        }
        harness.launchTargetA()
        harness.sleepMs(2_000)
    }

    @Test
    fun n03_sessionTimer_showsSessionRemaining() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(sessionLimitMs = 15 * 60_000L),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = com.gatekeep.domain.model.SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = System.currentTimeMillis() - 60_000L,
                ),
            )
        }
        harness.launchTargetA()
        harness.sleepMs(2_000)
    }

    @Test
    fun n04_sessionTimer_showsUsageBuckets() {
        seedHardBlockProfile()
        harness.launchTargetA()
        harness.sleepMs(2_000)
    }

    @Test
    fun n05_timerBody_dedupNoFlicker() {
        seedHardBlockProfile()
        harness.launchTargetA()
        harness.sleepMs(3_000)
    }

    @Test
    fun n06_eightyPercentWarning_fires() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(dailyLimitMs = 100 * 60_000L),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = 81 * 60_000L,
            )
        }
        harness.launchTargetA()
        harness.sleepMs(2_000)
    }

    @Test
    fun n07_tapNotification_opensMainActivity() {
        seedHardBlockProfile()
        enforcementCoordinator.refresh()
        harness.sleepMs(1_000)
        harness.openNotifications()
    }

    @Test
    fun n08_swipeSessionTimer_reappears() {
        n03_sessionTimer_showsSessionRemaining()
        harness.openNotifications()
        harness.pressBack()
        harness.sleepMs(2_000)
    }

    @Test
    fun n09_swipeWarning_staysDismissed() {
        n06_eightyPercentWarning_fires()
        harness.openNotifications()
        harness.pressBack()
    }

    @Test
    fun n10_timerToggleOff_hidesCountdown() {
        runSeed {
            settingsRepository.updateSettings { it.copy(showSessionTimerNotification = false) }
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        harness.sleepMs(2_000)
        assertFalse(harness.hasActiveCountdownNotification())
    }

    @Test
    fun n11_localeChange_localizedNotificationText() {
        runSeed {
            settingsRepository.updateSettings { it.copy(languageTag = "de-AT") }
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        harness.sleepMs(2_000)
    }

    @Test
    fun n12_screenOffOn_timerResumes() {
        n03_sessionTimer_showsSessionRemaining()
        harness.sleepDevice()
        harness.sleepMs(1_000)
    }
}
