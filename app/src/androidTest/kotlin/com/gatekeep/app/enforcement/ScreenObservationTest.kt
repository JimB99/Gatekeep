package com.gatekeep.app.enforcement

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads the overlay on the emulator. Foreground changes are not injected.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class ScreenObservationTest : EnforcementCrossAppTestBase() {

    @Before
    fun clearCrashLog() {
        harness.clearLogcat()
    }

    @After
    fun assertNoFatalCrash() {
        val crash = harness.gatekeepFatalCrash()
        assertTrue(crash ?: "no crash", crash == null)
    }

    @Test
    fun obs01_hardBlock_noInjection_overlayWithin2s() {
        seedHardBlockProfile()
        harness.useRealForegroundDetection()
        val launchStart = System.currentTimeMillis()
        harness.launchTargetA()
        val launchMs = System.currentTimeMillis() - launchStart
        val alreadyShown = harness.device.hasObject(
            androidx.test.uiautomator.By.res(EnforcementTestPackages.TARGET_A, "block_message"),
        )
        val extraStart = System.currentTimeMillis()
        val shown = alreadyShown || harness.waitForOverlay(2_000)
        val extraMs = if (alreadyShown) 0L else System.currentTimeMillis() - extraStart
        Log.i(TAG, "obs01 launch=${launchMs}ms extra=${extraMs}ms ${harness.screenDiagnostics()}")
        assertTrue("overlay missing ${harness.screenDiagnostics()}", shown)
        assertTrue("overlay took ${extraMs}ms after launch returned", extraMs <= 2_000)
    }

    @Test
    fun obs02_home_overlayGoneWithin1500ms() {
        seedHardBlockProfile()
        harness.launchTargetA()
        assertTrue("overlay never shown ${harness.screenDiagnostics()}", harness.waitForOverlay())
        harness.useRealForegroundDetection()
        val start = System.currentTimeMillis()
        harness.pressHome()
        val gone = harness.waitForOverlayGone(1_500)
        val hideMs = System.currentTimeMillis() - start
        Log.i(TAG, "obs02 overlay hidden in ${hideMs}ms ${harness.screenDiagnostics()}")
        assertTrue("overlay still visible after ${hideMs}ms ${harness.screenDiagnostics()}", gone)
        assertTrue("hide took ${hideMs}ms", hideMs <= 1_500)
    }

    @Test
    fun obs03_blockedOverlay_doesNotFlicker() {
        seedHardBlockProfile()
        harness.useRealForegroundDetection()
        harness.launchTargetA()
        assertTrue("overlay missing ${harness.screenDiagnostics()}", harness.waitForOverlay(2_000))
        harness.sleepMs(200)
        val first = harness.overlayMessageText()
        assertNotNull("message missing ${harness.screenDiagnostics()}", first)
        val deadline = System.currentTimeMillis() + 1_000
        var missing = 0
        var changes = 0
        var last = first
        while (System.currentTimeMillis() < deadline) {
            if (!harness.device.hasObject(By.res(harnessPackage(), "block_message"))) {
                missing++
            }
            val text = harness.overlayMessageText()
            if (last != null && text != null && text != last) changes++
            if (text != null) last = text
            harness.sleepMs(50)
        }
        Log.i(TAG, "obs03 missing=$missing changes=$changes message=$last")
        assertEquals("overlay disappeared", 0, missing)
        assertEquals("message flickered from $first to $last", 0, changes)
    }

    /**
     * Open-gate wait must appear without harness-injected foreground (real usage stats / a11y path).
     * Regression for delayed overlay until in-app taps on physical devices.
     */
    @Test
    fun obs08_openWait_noInjection_overlayWithin2s() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                    sessionLimitMs = null,
                    dailyLimitMs = null,
                ),
            )
        }
        harness.useRealForegroundDetection()
        val launchStart = System.currentTimeMillis()
        harness.launchTargetA()
        val launchMs = System.currentTimeMillis() - launchStart
        val extraStart = System.currentTimeMillis()
        val shown = harness.waitForOpenFriction(2_000) ||
            harness.waitForOverlay(2_000) ||
            harness.waitForCountdown(2_000) != null
        val extraMs = System.currentTimeMillis() - extraStart
        Log.i(TAG, "obs08 launch=${launchMs}ms extra=${extraMs}ms ${harness.screenDiagnostics()}")
        assertTrue("open wait missing ${harness.screenDiagnostics()}", shown)
        assertTrue("open wait took ${extraMs}ms after launch", extraMs <= 2_000)
    }

    @Test
    fun obs04_openWait_pausesWhileScreenOff() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = 12,
                    sessionLimitMs = null,
                    dailyLimitMs = null,
                ),
            )
        }
        assertCountdownPausesAcrossScreenOff()
    }

    @Test
    fun obs05_sessionWait_pausesWhileScreenOff() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onSessionLimitAction = OnSessionLimitAction.deterrentWait,
                    sessionWaitDurationSeconds = 12,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = GatekeepTestFixtures.continuedSession(
                    seeded.packageName,
                    GatekeepTestFixtures.TestDurations.SESSION_OVER_CAP_MS,
                ),
            )
        }
        assertCountdownPausesAcrossScreenOff()
    }

    @Test
    fun obs06_leaveOverOneMinute_freshSessionNotImmediateTimeout() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onSessionLimitAction = OnSessionLimitAction.hardBlock,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(
            "first session never blocked ${harness.screenDiagnostics()}",
            harness.waitForOverlay(8_000),
        )
        val firstMessage = harness.overlayMessageText().orEmpty()
        assertTrue(
            "expected a session block before leaving, was $firstMessage ${harness.screenDiagnostics()}",
            firstMessage.contains("Session"),
        )
        harness.useRealForegroundDetection()
        harness.pressHome()
        assertTrue(
            "overlay stayed after home ${harness.screenDiagnostics()}",
            harness.waitForOverlayGone(1_500),
        )
        harness.sleepMs(65_000)
        val start = System.currentTimeMillis()
        harness.launchTargetA()
        val shown = harness.waitForOverlay(10_000)
        val delayMs = System.currentTimeMillis() - start
        Log.i(TAG, "obs06 reopen overlay in ${delayMs}ms shown=$shown ${harness.screenDiagnostics()}")
        assertTrue("reopen was not detected ${harness.screenDiagnostics()}", shown)
        assertTrue("stale session overlay in ${delayMs}ms", delayMs >= 2_500)
    }

    @Test
    fun obs07_leaveFiveSeconds_sameSessionGapExcluded() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onSessionLimitAction = OnSessionLimitAction.hardBlock,
                    sessionLimitMs = 6_000L,
                    dailyLimitMs = null,
                ),
            )
        }
        harness.launchTargetA()
        harness.sleepMs(2_000)
        assertTrue(
            "session blocked before the leave ${harness.screenDiagnostics()}",
            harness.overlayMessageText() == null,
        )
        harness.useRealForegroundDetection()
        harness.pressHome()
        harness.sleepMs(5_000)
        harness.launchTargetA()
        val shown = harness.waitForOverlay(1_500)
        Log.i(TAG, "obs07 quick return overlay=$shown ${harness.screenDiagnostics()}")
        assertTrue(
            "gap was counted or the session timed out ${harness.screenDiagnostics()}",
            !shown,
        )
    }

    private fun assertCountdownPausesAcrossScreenOff(tapContinue: Boolean = false) {
        harness.wakeDevice()
        harness.launchTargetA()
        if (tapContinue) {
            assertTrue(
                "session wait overlay missing ${harness.screenDiagnostics()}",
                harness.waitForOverlay(),
            )
            harness.clickOverlayContinue()
        }
        assertTrue("countdown missing ${harness.screenDiagnostics()}", harness.waitForCountdown() != null)
        val atOff = harness.sleepDeviceCapturingCountdown()
        assertTrue("screen did not turn off ${harness.screenDiagnostics()}", !harness.isScreenOn())
        assertTrue("countdown unreadable at lock ${harness.screenDiagnostics()}", atOff != null && atOff >= 8)
        harness.sleepMs(3_000)
        val stillOff = !harness.isScreenOn()
        harness.wakeDevice()
        harness.sleepMs(400)
        val after = harness.countdownSeconds()
        Log.i(TAG, "countdown atOff=$atOff after=$after stillOff=$stillOff ${harness.screenDiagnostics()}")
        assertTrue("screen woke during the lock", stillOff)
        assertTrue(
            "countdown ran through lock atOff=$atOff after=$after ${harness.screenDiagnostics()}",
            after != null && atOff!! - after <= 1,
        )
    }

    private fun harnessPackage(): String = EnforcementTestPackages.TARGET_A

    companion object {
        private const val TAG = "GatekeepScreenObs"
    }
}
