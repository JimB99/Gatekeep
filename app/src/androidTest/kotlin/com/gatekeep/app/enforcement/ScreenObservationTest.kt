package com.gatekeep.app.enforcement

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.SessionTracker
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import kotlinx.coroutines.runBlocking
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
    fun obs04_openWait_resetsOnScreenOff() {
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
        assertCountdownResetsAcrossScreenOff(fullWaitSeconds = 12)
    }

    @Test
    fun obs05_sessionWait_resetsOnScreenOff() {
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
        assertCountdownResetsAcrossScreenOff(fullWaitSeconds = 12)
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

    /**
     * OBS-09: Home without opening another app must stamp leave and freeze the session
     * clock within ~1s (YouTube staying "current" in usage stats must not keep ticking).
     */
    @Test
    fun obs09_home_freezesSessionWithoutOtherApp() {
        val pkg = EnforcementTestPackages.TARGET_A
        val sessionLimitMs = 30_000L
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    sessionLimitMs = sessionLimitMs,
                    dailyLimitMs = null,
                ),
            )
        }
        harness.wakeDevice()
        harness.launchTargetA()
        harness.sleepMs(1_500)
        harness.useRealForegroundDetection()
        harness.pressHome()
        harness.sleepMs(1_000)
        val leftState = runBlocking { usageRepository.getSessionState(seeded.profileId, pkg) }
        assertTrue(
            "home must stamp lastForegroundEnd within 1s ${harness.screenDiagnostics()} state=$leftState",
            leftState?.lastForegroundEndEpochMs != null,
        )
        val durationAtLeave = SessionTracker.sessionDurationMs(leftState, System.currentTimeMillis())
        harness.sleepMs(3_000)
        val laterState = runBlocking { usageRepository.getSessionState(seeded.profileId, pkg) }
        val durationLater = SessionTracker.sessionDurationMs(laterState, System.currentTimeMillis())
        assertTrue(
            "session clock must stay frozen on home durationAtLeave=$durationAtLeave later=$durationLater ${harness.screenDiagnostics()}",
            kotlin.math.abs(durationLater - durationAtLeave) <= 1_000L,
        )
        assertTrue(
            "trace: session_leave or hud_freeze after home",
            enforcementCoordinator.enforcementTraceCount(EnforcementTrace.SESSION_LEAVE) >= 1 ||
                enforcementCoordinator.enforcementTraceCount(EnforcementTrace.HUD_FREEZE) >= 1,
        )
    }

    private fun assertCountdownResetsAcrossScreenOff(fullWaitSeconds: Int) {
        harness.wakeDevice()
        harness.launchTargetA()
        assertTrue("countdown missing ${harness.screenDiagnostics()}", harness.waitForCountdown() != null)
        val dropDeadline = System.currentTimeMillis() + 4_000
        var beforeLock: Int? = null
        while (System.currentTimeMillis() < dropDeadline) {
            beforeLock = harness.countdownSeconds()
            if (beforeLock != null && beforeLock <= fullWaitSeconds - 2) break
            harness.sleepMs(200)
        }
        assertTrue(
            "countdown did not drop before lock before=$beforeLock ${harness.screenDiagnostics()}",
            beforeLock != null && beforeLock <= fullWaitSeconds - 2,
        )
        val atOff = harness.sleepDeviceCapturingCountdown() ?: beforeLock
        assertTrue("screen did not turn off ${harness.screenDiagnostics()}", !harness.isScreenOn())
        assertTrue(
            "countdown unreadable at lock atOff=$atOff ${harness.screenDiagnostics()}",
            atOff != null && atOff <= fullWaitSeconds - 2,
        )
        harness.sleepMs(3_000)
        val stillOff = !harness.isScreenOn()
        harness.wakeDevice()
        val after = harness.waitForCountdown(3_000)
        Log.i(TAG, "countdown atOff=$atOff after=$after stillOff=$stillOff ${harness.screenDiagnostics()}")
        assertTrue("screen woke during the lock", stillOff)
        assertTrue(
            "in-progress wait must reset on lock, not keep leftover atOff=$atOff after=$after ${harness.screenDiagnostics()}",
            after != null && after >= fullWaitSeconds - 1,
        )
    }

    private fun harnessPackage(): String = EnforcementTestPackages.TARGET_A

    companion object {
        private const val TAG = "GatekeepScreenObs"
    }
}
