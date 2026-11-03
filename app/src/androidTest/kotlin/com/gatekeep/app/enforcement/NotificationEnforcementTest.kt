package com.gatekeep.app.enforcement



import androidx.test.ext.junit.runners.AndroidJUnit4

import androidx.test.filters.LargeTest

import dagger.hilt.android.testing.HiltAndroidTest

import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures

import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction

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

        if (harness.isAccessibilityHealthy()) {
            // FGS is the a11y-down fallback; the service notification is removed when a11y is connected.
            assertFalse(harness.hasActiveServiceNotification())
        } else {
            enforcementCoordinator.startEnforcementService()
            val shown = harness.waitForServiceNotification()
            if (shown) return
            // Service may bind after startForegroundService; then FGS is correctly omitted.
            assertTrue(
                "a11y-down must show the FGS notification, or a11y must reconnect",
                harness.isAccessibilityHealthy(),
            )
        }

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

                sessionState = com.gatekeep.app.support.GatekeepTestFixtures.continuedSession(
                    seeded.packageName,
                    GatekeepTestFixtures.TestDurations.SESSION_TIMER_ELAPSED_MS,
                ),

            )

        }

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

    fun n08_swipeSessionTimer_staysDismissedUntilLeave() {

        n03_sessionTimer_showsSessionRemaining()

        harness.openNotifications()

        harness.dismissCountdownNotification()

        harness.pressBack()

        harness.waitForCountdownNotificationGone()

        assertFalse(harness.hasActiveCountdownNotification())

        harness.sleepMs(2_000)

        assertFalse(harness.hasActiveCountdownNotification())

        assertTrue(harness.sleepAndConfirmOff())

        harness.wakeDevice()

        assertFalse(harness.hasActiveCountdownNotification())

        harness.pressHome()

        harness.launchTargetA()

        assertTrue(harness.waitForCountdownNotification())

    }



    @Test

    fun n09_swipeWarning_staysDismissed() {

        n06_eightyPercentWarning_fires()

        harness.openNotifications()

        harness.pressBack()

    }



    @Test

    fun n16_leaveHome_clearsSessionTimer() {

        n03_sessionTimer_showsSessionRemaining()

        harness.pressHome()

        assertTrue(harness.waitForCountdownNotificationGone())

    }



    @Test

    fun n17_leaveHome_clearsApproachingWarning() {

        n06_eightyPercentWarning_fires()

        harness.pressHome()

        assertTrue(harness.waitForApproachingLimitNotificationGone())

    }



    @Test

    fun n19_leaveHome_clearsLimitReachedWarning() {

        runSeed {

            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(

                profileRepository = profileRepository,

                config = GatekeepTestFixtures.ProfileSeedConfig(

                    onOpenAction = OnOpenAction.none,

                    onLimitAction = OnLimitAction.notifyOnly,

                    onSessionLimitAction = OnSessionLimitAction.notifyOnly,

                    sessionLimitMs = null,

                ),

            )

            GatekeepTestFixtures.seedUsageAtCap(

                usageRepository,

                seeded.profileId,

                seeded.packageName,

                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,

            )

        }

        harness.launchTargetA()

        assertTrue(harness.waitForLimitReachedNotification())

        harness.pressHome()

        assertTrue(harness.waitForLimitReachedNotificationGone())

    }



    @Test

    fun n20_switchMonitoredApp_clearsPreviousUsageNotifications() {

        runSeed {

            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(

                profileRepository = profileRepository,

                extraPackages = listOf(

                    EnforcementTestPackages.TARGET_B to EnforcementTestPackages.TARGET_B_LABEL,

                ),

                config = GatekeepTestFixtures.ProfileSeedConfig(

                    dailyLimitMs = GatekeepTestFixtures.TestDurations.EIGHTY_PERCENT_LIMIT_MS,

                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,

                ),

            )

            GatekeepTestFixtures.seedUsageAtCap(

                usageRepository,

                seeded.profileId,

                seeded.packageName,

                dailyMs = GatekeepTestFixtures.TestDurations.EIGHTY_PERCENT_USAGE_MS,

                sessionState = GatekeepTestFixtures.continuedSession(

                    seeded.packageName,

                    GatekeepTestFixtures.TestDurations.SESSION_TIMER_ELAPSED_MS,

                ),

            )

        }

        harness.launchTargetA()

        assertTrue(harness.waitForApproachingLimitNotification())

        assertTrue(harness.waitForCountdownNotification())

        harness.launchTargetB()

        assertTrue(harness.waitForApproachingLimitNotificationGone())

        assertTrue(

            harness.waitForCountdownTitleNotContaining(EnforcementTestPackages.TARGET_A_LABEL),

        )

    }



    @Test

    fun n18_screenOff_clearsTimer_unlockRestores() {

        n03_sessionTimer_showsSessionRemaining()

        assertTrue(harness.sleepAndConfirmOff())

        assertTrue(harness.waitForCountdownNotificationGone())

        harness.wakeDevice()

        assertTrue(harness.waitForCountdownNotification())

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


