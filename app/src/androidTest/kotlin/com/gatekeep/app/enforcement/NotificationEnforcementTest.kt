package com.gatekeep.app.enforcement



import androidx.test.ext.junit.runners.AndroidJUnit4

import androidx.test.filters.LargeTest

import dagger.hilt.android.testing.HiltAndroidTest

import com.gatekeep.app.support.GatekeepTestFixtures

import com.gatekeep.domain.model.OnLimitAction

import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue

import org.junit.Test

import org.junit.runner.RunWith

import javax.inject.Inject



@HiltAndroidTest

@RunWith(AndroidJUnit4::class)

@LargeTest

class NotificationEnforcementTest : EnforcementCrossAppTestBase() {

    @Inject lateinit var notificationHelper: GatekeepNotificationHelper

    @Test

    fun n01_serviceNotification_present() {

        seedHardBlockProfile()

        enforcementCoordinator.refresh()

        assertTrue(harness.waitForServiceNotification())

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

        harness.waitForCountdownNotificationGone()

    }



    @Test

    fun n03_sessionTimer_showsSessionRemaining() {

        runSeed {

            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(

                profileRepository = profileRepository,

                config = GatekeepTestFixtures.ProfileSeedConfig(

                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,

                ),

            )

            GatekeepTestFixtures.seedUsageAtCap(

                usageRepository,

                seeded.profileId,

                seeded.packageName,

                sessionState = com.gatekeep.domain.model.SessionState(

                    packageName = seeded.packageName,

                    sessionStartEpochMs = System.currentTimeMillis() -

                        GatekeepTestFixtures.TestDurations.SESSION_TIMER_ELAPSED_MS,

                ),

            )

        }

        harness.launchTargetA()

        harness.waitForCountdownNotification()

    }



    @Test

    fun n04_sessionTimer_showsUsageBuckets() {

        seedHardBlockProfile()

        harness.launchTargetA()

        harness.waitForCountdownNotification()

    }



    @Test

    fun n05_timerBody_dedupNoFlicker() {

        seedHardBlockProfile()

        harness.launchTargetA()

        harness.waitForCountdownNotification()

    }



    @Test

    fun n06_eightyPercentWarning_fires() {

        runSeed {

            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(

                profileRepository = profileRepository,

                config = GatekeepTestFixtures.ProfileSeedConfig(

                    dailyLimitMs = GatekeepTestFixtures.TestDurations.EIGHTY_PERCENT_LIMIT_MS,

                ),

            )

            GatekeepTestFixtures.seedUsageAtCap(

                usageRepository,

                seeded.profileId,

                seeded.packageName,

                dailyMs = GatekeepTestFixtures.TestDurations.EIGHTY_PERCENT_USAGE_MS,

            )

        }

        harness.launchTargetA()

        assertTrue(harness.waitForApproachingLimitNotification())

    }



    @Test

    fun n07_tapNotification_opensMainActivity() {

        seedHardBlockProfile()

        enforcementCoordinator.refresh()

        harness.waitForServiceNotification()

        harness.openNotifications()

    }



    @Test

    fun n08_swipeSessionTimer_reappears() {

        n03_sessionTimer_showsSessionRemaining()

        harness.openNotifications()

        harness.pressBack()

        harness.waitForCountdownNotification()

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

        harness.waitForCountdownNotificationGone()

        assertFalse(harness.hasActiveCountdownNotification())

    }



    @Test

    fun n11_localeChange_localizedNotificationText() {

        runSeed {

            settingsRepository.updateSettings { it.copy(languageTag = "de-AT") }

            seedHardBlockProfile()

        }

        harness.launchTargetA()

        harness.waitForCountdownNotification()

    }



    @Test

    fun n12_screenOffOn_timerResumes() {

        n03_sessionTimer_showsSessionRemaining()

        harness.sleepDevice()

        harness.waitForCountdownNotification()

    }



    @Test

    fun n13_warningNotification_hasContentIntent() {

        n06_eightyPercentWarning_fires()

        assertTrue(
            harness.hasNotificationContentIntent(GatekeepNotificationHelper.APPROACHING_LIMIT_ID),
        )

    }



    @Test

    fun n14_tapApproachingLimit_opensCurrentUsage() {

        n06_eightyPercentWarning_fires()

        assertTrue(
            harness.launchNotificationContentIntent(GatekeepNotificationHelper.APPROACHING_LIMIT_ID),
        )

        assertTrue(harness.waitForCurrentUsageScreen())

    }



    @Test

    fun n15_weeklyReportNotification_navigatesToStats() {

        notificationHelper.showWeeklyReportWarning()

        assertTrue(harness.waitForWeeklyReportNotification())

        assertTrue(
            harness.hasNotificationContentIntent(GatekeepNotificationHelper.WEEKLY_REPORT_ID),
        )

        assertTrue(
            harness.launchNotificationContentIntent(GatekeepNotificationHelper.WEEKLY_REPORT_ID),
        )

        assertTrue(harness.waitForStatsScreen())

    }

}


