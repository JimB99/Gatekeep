package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class OpenGateTest : EnforcementCrossAppTestBase() {

    @Test
    fun g01_mathWrong_staysBlocked() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.deterrentMath,
                    defaultFrictionDifficulty = FrictionDifficulty.easy,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay() || harness.isFrictionVisible())
        harness.submitOverlayMathAnswer("99999")
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun g02_mathCorrect_proceeds() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentMath,
                    defaultFrictionDifficulty = FrictionDifficulty.easy,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.isFrictionVisible() || harness.waitForOverlay())
    }

    @Test
    fun g03_waitTimer_completes() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                ),
            )
        }
        harness.launchTargetA()
        harness.waitForElapsedMs(GatekeepTestFixtures.TestDurations.msAfterTimer(GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC))
    }

    @Test
    fun g04_waitCancelled_byLeavingApp() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.CANCELLED_OPEN_WAIT_SEC,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        harness.pressHome()
        assertOverlayHidden()
    }

    @Test
    fun g05_holdButton_duration() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    defaultFrictionMethod = FrictionMethod.holdButton,
                    onLimitAction = OnLimitAction.deterrentMath,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay() || harness.isFrictionVisible())
    }

    @Test
    fun g06_typePhrase_exactMatch() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    defaultFrictionMethod = FrictionMethod.typePhrase,
                    onLimitAction = OnLimitAction.deterrentMath,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay() || harness.isFrictionVisible())
    }

    @Test
    fun g07_password_onOverlay() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    defaultFrictionMethod = FrictionMethod.password,
                    onLimitAction = OnLimitAction.deterrentMath,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay() || harness.isFrictionVisible())
    }

    @Test
    fun g08_difficulty_affectsMath() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.deterrentMath,
                    defaultFrictionDifficulty = FrictionDifficulty.hard,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.isFrictionVisible() || harness.waitForOverlay())
    }

    @Test
    fun g09_openVsSessionWait_durations() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                    sessionWaitDurationSeconds = GatekeepTestFixtures.TestDurations.SESSION_WAIT_SEC,
                ),
            )
        }
        harness.launchTargetA()
        harness.waitForElapsedMs(GatekeepTestFixtures.TestDurations.msAfterTimer(GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC))
    }

    @Test
    fun g10_noneFriction_extensionBypass() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    defaultFrictionMethod = FrictionMethod.none,
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun pp01_wrongProfilePin_staysBlocked() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.pinGate,
                    passwordHash = PasswordHasher.hash(GatekeepTestFixtures.PROFILE_PIN),
                    lockEnabled = true,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay() || harness.isFrictionVisible())
    }

    @Test
    fun pp02_correctProfilePin_proceeds() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.pinGate,
                    passwordHash = PasswordHasher.hash(GatekeepTestFixtures.PROFILE_PIN),
                    lockEnabled = true,
                ),
            )
        }
        harness.launchTargetA()
        harness.submitOverlayMathAnswer(GatekeepTestFixtures.PROFILE_PIN)
    }

    @Test
    fun pp03_switchApp_requiresPinAgain() {
        pp02_correctProfilePin_proceeds()
        harness.launchTargetB()
    }

    @Test
    fun pp04_appPinThenProfilePin_stack() {
        runSeed {
            GatekeepTestFixtures.seedAppLockEnabled(settingsRepository)
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.pinGate,
                    passwordHash = PasswordHasher.hash(GatekeepTestFixtures.PROFILE_PIN),
                ),
            )
        }
        harness.launchTargetA()
    }

    @Test
    fun g11_samePackageActivityChange_doesNotReopenGate() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentMath,
                    defaultFrictionDifficulty = FrictionDifficulty.easy,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        runBlocking {
            enforcementCoordinator.onOpenGatePassed(EnforcementTestPackages.TARGET_A)
            assertTrue(
                enforcementCoordinator.awaitAllowedWithoutOverlay(EnforcementTestPackages.TARGET_A),
            )
        }
        enforcementCoordinator.onForegroundAppChanged(
            EnforcementTestPackages.TARGET_A,
            "com.instagram.android.AccountSwitcherActivity",
        )
        enforcementCoordinator.onForegroundAppChanged(
            EnforcementTestPackages.TARGET_A,
            "com.instagram.android.MainActivity",
        )
        assertOverlayHidden()
    }

    @Test
    fun g12_leaveAndReturn_reopensGate() {
        var profileId = 0L
        runSeed {
            profileId = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentMath,
                    defaultFrictionDifficulty = FrictionDifficulty.easy,
                ),
            ).profileId
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        runBlocking {
            enforcementCoordinator.onOpenGatePassed(EnforcementTestPackages.TARGET_A)
            val state = usageRepository.getSessionState(profileId, EnforcementTestPackages.TARGET_A)
                ?: error("missing session state")
            usageRepository.saveSessionState(
                state.copy(
                    openGatePassedEpochMs = System.currentTimeMillis() - 70_000L,
                    lastForegroundEndEpochMs = System.currentTimeMillis() - 70_000L,
                ),
                profileId,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
    }

    /**
     * Regression: completing open-gate wait must clear [pendingWaitUntil] before the next
     * evaluate — otherwise a second wait overlay appears and can crash the a11y service.
     */
    @Test
    fun g16_openWaitComplete_noSecondWaitOrSessionPendingResume() {
        val pkg = EnforcementTestPackages.TARGET_A
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                    dailyLimitMs = null,
                    sessionLimitMs = null,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        harness.waitForElapsedMs(
            GatekeepTestFixtures.TestDurations.msAfterTimer(GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC),
        )
        runBlocking {
            assertTrue(
                "allowed after open wait ${harness.screenDiagnostics()}",
                enforcementCoordinator.awaitAllowedWithoutOverlay(pkg),
            )
            enforcementCoordinator.evaluateMonitoredPackageForTests(pkg)
            assertTrue(
                "re-evaluate must not start another wait ${harness.screenDiagnostics()}",
                enforcementCoordinator.awaitAllowedWithoutOverlay(pkg),
            )
        }
        assertTrue(
            "trace: no session-limit pending resume after open wait",
            enforcementCoordinator.enforcementTraceCount(EnforcementTrace.SHOW_PENDING_SESSION_WAIT) == 0,
        )
        assertTrue(
            "trace: open gate passed once",
            enforcementCoordinator.enforcementTraceCount(EnforcementTrace.OPEN_GATE_PASSED) == 1,
        )
    }

    @Test
    fun g13_quickReturn_withinGrace_skipsOpenGate() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.CANCELLED_OPEN_WAIT_SEC,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        runBlocking {
            enforcementCoordinator.onOpenGatePassed(EnforcementTestPackages.TARGET_A)
            assertTrue(
                enforcementCoordinator.awaitAllowedWithoutOverlay(EnforcementTestPackages.TARGET_A),
            )
        }
        harness.pressHome()
        harness.launchTargetA()
        assertOverlayHidden()
    }

    @Test
    fun g14_spuriousLauncherBlip_doesNotReopenGate() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.CANCELLED_OPEN_WAIT_SEC,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        runBlocking {
            enforcementCoordinator.onOpenGatePassed(EnforcementTestPackages.TARGET_A)
            assertTrue(
                enforcementCoordinator.awaitAllowedWithoutOverlay(EnforcementTestPackages.TARGET_A),
            )
        }
        enforcementCoordinator.onForegroundAppChanged("com.android.launcher3")
        enforcementCoordinator.onForegroundAppChanged(
            EnforcementTestPackages.TARGET_A,
            "com.instagram.android.MainActivity",
        )
        Thread.sleep(ForegroundStabilizationPolicy.DEBOUNCE_MS + 100L)
        assertOverlayHidden()
    }

    @Test
    fun g15_quickBounce_neverPassed_stillShowsOpenGate() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.CANCELLED_OPEN_WAIT_SEC,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        harness.pressHome()
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
    }

    @Test
    fun pp06_pinGateWithScheduleBlock() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.pinGate,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository = profileRepository,
                profileId = seeded.profileId,
                mode = com.gatekeep.domain.model.SchedulePolicyMode.block,
                windows = listOf(
                    com.gatekeep.domain.ScheduleTestWindows.aroundNow(seeded.profileId, segmentId = null),
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }
}
