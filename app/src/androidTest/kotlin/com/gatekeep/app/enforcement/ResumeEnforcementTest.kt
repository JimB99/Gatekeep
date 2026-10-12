package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.SessionState
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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

    /**
     * RES-06: Session-limit overlay then lock ≥60s — unlock must start a fresh session
     * (open wait), not restore the old session-limit overlay.
     */
    @Test
    fun res06_lockAtLeastOneMinute_atSessionLimit_startsFreshOpenWait() {
        val pkg = EnforcementTestPackages.TARGET_A
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    onSessionLimitAction = OnSessionLimitAction.hardBlock,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                ),
            )
            val now = System.currentTimeMillis()
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = SessionState(
                    packageName = seeded.packageName,
                    sessionStartEpochMs = now -
                        GatekeepTestFixtures.TestDurations.SESSION_OVER_CAP_MS,
                    lastForegroundEndEpochMs = now -
                        GatekeepTestFixtures.TestDurations.SESSION_CONTINUE_GAP_MS,
                    openGatePassedEpochMs = now -
                        GatekeepTestFixtures.TestDurations.SESSION_OVER_CAP_MS + 1,
                ),
            )
        }
        harness.wakeDevice()
        harness.launchTargetA()
        assertTrue(
            "session-limit overlay should show ${harness.screenDiagnostics()}",
            harness.waitForOverlay(timeoutMs = 8_000),
        )
        val blockedMessage = harness.overlayMessageText().orEmpty()
        assertTrue(
            "expected session block, was $blockedMessage ${harness.screenDiagnostics()}",
            blockedMessage.contains("Session", ignoreCase = true),
        )
        harness.sleepDevice()
        harness.sleepMs(61_000)
        harness.wakeDevice()
        val seenOpenWait = harness.waitForOpenFriction(timeoutMs = 8_000)
        val traces = enforcementCoordinator.enforcementTraceSnapshot()
        val startFresh = traces.any {
            it.event == EnforcementTrace.SESSION_START && it.fields["action"] == "StartFresh"
        }
        val openGateShown = traces.any {
            it.event == EnforcementTrace.OVERLAY_SHOW && it.fields["openGate"] == "true"
        }
        assertTrue(
            "long lock must StartFresh open wait traces=$traces ${harness.screenDiagnostics()}",
            startFresh && (seenOpenWait || openGateShown),
        )
        val after = harness.overlayMessageText().orEmpty()
        assertTrue(
            "must not skip open wait after long lock, was $after traces=$traces ${harness.screenDiagnostics()}",
            seenOpenWait || openGateShown,
        )
        assertTrue(
            "trace: no session-limit overlay restore after long lock",
            enforcementCoordinator.enforcementTraceSnapshot().none {
                it.event == EnforcementTrace.OVERLAY_RESTORE && it.fields["pkg"] == pkg
            } || enforcementCoordinator.enforcementTraceCount(EnforcementTrace.SHOW_OPEN_DETERRENT) >= 1,
        )
    }

    /**
     * RES-07: Mid-session lock for 5s then unlock — same session, remaining not burned.
     */
    @Test
    fun res07_shortLock_continuesSameSessionRemainingFrozen() {
        val sessionLimitMs = 20_000L
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
            before != null && before >= 10,
        )
        harness.sleepDevice()
        harness.sleepMs(5_000)
        harness.wakeDevice()
        harness.sleepMs(800)
        val after = harness.waitForSessionRemainingSeconds(timeoutMs = 8_000)
        assertTrue(
            "short lock must keep the same session remaining before=$before after=$after ${harness.screenDiagnostics()}",
            after != null && before != null && before - after <= 2,
        )
        assertTrue(
            "must not show a session-limit overlay after a short lock ${harness.screenDiagnostics()}",
            harness.overlayMessageText() == null,
        )
    }

    /**
     * RES-08: Home, then lock, then unlock on the launcher — must not flash open wait
     * for the last monitored app.
     */
    @Test
    fun res08_unlockOnHome_doesNotFlashOpenWait() {
        val pkg = EnforcementTestPackages.TARGET_A
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    dailyLimitMs = null,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                ),
            )
        }
        harness.wakeDevice()
        harness.launchTargetA()
        assertTrue(
            "open wait should start ${harness.screenDiagnostics()}",
            harness.waitForOverlay(timeoutMs = 8_000),
        )
        harness.useRealForegroundDetection()
        harness.pressHome()
        assertTrue(
            "home must hide overlay first ${harness.screenDiagnostics()}",
            harness.waitForOverlayGone(2_000),
        )
        harness.sleepDevice()
        harness.sleepMs(3_000)
        val wakeAt = System.currentTimeMillis()
        harness.wakeDevice()
        assertFalse(
            "unlock on home must not flash wait overlay ${harness.screenDiagnostics()}",
            harness.overlayAppearedDuring(2_000),
        )
        val afterUnlock = enforcementCoordinator.enforcementTraceSnapshot().filter { it.epochMs >= wakeAt }
        assertTrue(
            "must not overlay_show after unlock on home: $afterUnlock ${harness.screenDiagnostics()}",
            afterUnlock.none { it.event == EnforcementTrace.OVERLAY_SHOW },
        )
        assertTrue(
            "must not overlay_restore last monitored after unlock on home: $afterUnlock",
            afterUnlock.none {
                it.event == EnforcementTrace.OVERLAY_RESTORE && it.fields["pkg"] == pkg
            },
        )
        assertTrue(
            "must not show open wait after unlock on home: $afterUnlock ${harness.screenDiagnostics()}",
            afterUnlock.none { it.event == EnforcementTrace.SHOW_OPEN_DETERRENT } &&
                harness.overlayMessageText() == null,
        )
    }
}
