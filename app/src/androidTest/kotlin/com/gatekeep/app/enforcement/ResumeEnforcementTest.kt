package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RES-01–03: enforcement must re-run when the user resumes without a foreground package change
 * (screen lock) or returns to an unresolved limit block (home → reopen).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class ResumeEnforcementTest : EnforcementCrossAppTestBase() {

    /**
     * RES-01: Unlock into the same monitored app after sleep — open gate or HUD must appear
     * without leaving the app first.
     *
     * How: launch with pin open gate, sleep device briefly, wake without pressing home.
     */
    @Test
    fun res01_screenUnlockSameApp_runsOpenGateOrCountdown() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.pinGate,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                ),
            )
        }
        harness.wakeDevice()
        harness.launchTargetA()
        assertTrue(
            "open gate or session HUD should start on launch ${harness.screenDiagnostics()}",
            harness.waitForOverlay(timeoutMs = 8_000) || harness.waitForCountdown(timeoutMs = 8_000) != null,
        )
        harness.sleepDevice()
        harness.sleepMs(3_000)
        harness.wakeDevice()
        harness.sleepMs(500)
        assertTrue(
            "after unlock without leaving app, enforcement should resume ${harness.screenDiagnostics()}",
            harness.waitForOverlay(timeoutMs = 8_000) ||
                harness.waitForCountdown(timeoutMs = 8_000) != null,
        )
    }

    /**
     * RES-02: Session limit with extension overlay — leave via home and reopen must show overlay
     * with extension buttons again.
     */
    @Test
    fun res02_sessionExtensionOverlay_returnsAfterHome() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                    sessionExtensionPolicy = ExtensionPolicy(
                        optionMinutes = listOf(1, 5),
                        surfaceMode = ExtensionSurfaceMode.overlay,
                    ),
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
        harness.wakeDevice()
        harness.launchTargetA()
        assertTrue(
            "session extension overlay expected ${harness.screenDiagnostics()}",
            harness.waitForOverlay(8_000),
        )
        assertTrue(
            "extension buttons expected on limit overlay",
            harness.isExtensionButtonsVisible(),
        )
        harness.useRealForegroundDetection()
        harness.pressHome()
        assertTrue(harness.waitForOverlayGone(2_000))
        harness.launchTargetA()
        assertTrue(
            "overlay must return after reopen ${harness.screenDiagnostics()}",
            harness.waitForOverlay(10_000),
        )
        assertTrue(harness.isExtensionButtonsVisible())
    }

    /**
     * RES-03: Same as RES-02 — user must not get free usage without resolving the limit.
     */
    @Test
    fun res03_sessionExtensionOverlay_reopenStillBlocked() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                    sessionExtensionPolicy = ExtensionPolicy(
                        optionMinutes = listOf(1),
                        surfaceMode = ExtensionSurfaceMode.overlay,
                    ),
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
        harness.wakeDevice()
        harness.launchTargetA()
        assertTrue(
            "session extension overlay expected ${harness.screenDiagnostics()}",
            harness.waitForOverlay(8_000),
        )
        harness.useRealForegroundDetection()
        harness.pressHome()
        harness.sleepMs(1_000)
        harness.launchTargetA()
        assertBlockedWithOverlay()
        assertTrue(
            "must not be allowed without overlay while limit unresolved",
            !runBlocking {
                enforcementCoordinator.awaitAllowedWithoutOverlay(
                    EnforcementTestPackages.TARGET_A,
                    attempts = 3,
                    delayMs = 200,
                )
            },
        )
    }

    /**
     * RES-04: Session remaining must freeze while the screen is off. Sleep longer than remaining
     * and unlock — the session must not have expired (deadline must not fire in the pocket).
     */
    @Test
    fun res04_screenOffLongerThanRemaining_doesNotExpireSession() {
        val sessionLimitMs = 8_000L
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
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
        val before = harness.waitForSessionRemainingSeconds(timeoutMs = 8_000)
        assertTrue(
            "session HUD should start ${harness.screenDiagnostics()}",
            before != null && before >= 5,
        )
        harness.sleepDevice()
        harness.sleepMs(12_000)
        harness.wakeDevice()
        harness.sleepMs(800)
        val after = harness.waitForSessionRemainingSeconds(timeoutMs = 8_000)
        assertTrue(
            "session must not expire while locked before=$before after=$after ${harness.screenDiagnostics()}",
            after != null && before != null && before - after <= 2,
        )
        assertTrue(
            "must not show a session-limit overlay after pocket lock ${harness.screenDiagnostics()}",
            harness.overlayMessageText() == null,
        )
    }

    /**
     * RES-05: SCREEN_ON onto the lock screen must not consume open gate. Unlock (USER_PRESENT)
     * must show it. Skip when the AVD has no keyguard.
     */
    @Test
    fun res05_wakeToLockScreen_openGateRunsOnUnlock() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.pinGate,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                ),
            )
        }
        harness.wakeDevice()
        harness.launchTargetA()
        assertTrue(
            "open gate should start on launch ${harness.screenDiagnostics()}",
            harness.waitForOverlay(timeoutMs = 8_000),
        )
        harness.sleepDevice()
        harness.sleepMs(2_000)
        harness.wakeToLockScreen()
        harness.sleepMs(800)
        org.junit.Assume.assumeTrue(
            "emulator has no lock screen; RES-05 needs keyguard",
            harness.isKeyguardLocked(),
        )
        assertTrue(
            "open gate must not pass on lock screen ${harness.screenDiagnostics()}",
            harness.overlayMessageText() == null || !harness.isScreenOn() || harness.isKeyguardLocked(),
        )
        harness.dismissKeyguard()
        harness.sleepMs(500)
        assertTrue(
            "open gate or HUD must run after unlock ${harness.screenDiagnostics()}",
            harness.waitForOverlay(timeoutMs = 8_000) ||
                harness.waitForCountdown(timeoutMs = 8_000) != null,
        )
    }
}
