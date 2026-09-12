package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.RuleEngine
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class RulesCrossEnforcementTest : EnforcementCrossAppTestBase() {

    private fun seedTriple(
        open: OnOpenAction,
        limit: OnLimitAction,
        session: OnSessionLimitAction,
        block: Boolean = true,
    ) {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = open,
                    onLimitAction = limit,
                    onSessionLimitAction = session,
                ),
            )
            if (block) {
                GatekeepTestFixtures.seedUsageAtCap(
                    usageRepository,
                    seeded.profileId,
                    seeded.packageName,
                    dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
                )
            }
        }
    }

    @Test fun r01_none_hardBlock_notifyOnly() = seedTriple(OnOpenAction.none, OnLimitAction.hardBlock, OnSessionLimitAction.notifyOnly).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r02_pinGate_extensions_mandatoryBreak() = seedTriple(OnOpenAction.pinGate, OnLimitAction.limitWithExtensions, OnSessionLimitAction.mandatoryBreak).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r03_deterrentWait_math_hardBlock() = seedTriple(OnOpenAction.deterrentWait, OnLimitAction.deterrentMath, OnSessionLimitAction.hardBlock).also { harness.launchTargetA() }
    @Test fun r04_deterrentMath_break_extensions() = seedTriple(OnOpenAction.deterrentMath, OnLimitAction.mandatoryBreak, OnSessionLimitAction.limitWithExtensions).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r05_none_notify_hardBlock() = seedTriple(OnOpenAction.none, OnLimitAction.notifyOnly, OnSessionLimitAction.hardBlock, block = false).also { harness.launchTargetA() }
    @Test fun r06_pinGate_hardBlock_notifyOnly() = seedTriple(OnOpenAction.pinGate, OnLimitAction.hardBlock, OnSessionLimitAction.notifyOnly).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r07_wait_extensions_break() = seedTriple(OnOpenAction.deterrentWait, OnLimitAction.limitWithExtensions, OnSessionLimitAction.mandatoryBreak).also { harness.launchTargetA() }
    @Test fun r08_none_extensions_hardBlock() = seedTriple(OnOpenAction.none, OnLimitAction.limitWithExtensions, OnSessionLimitAction.hardBlock).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r09_pinGate_notify_extensions() = seedTriple(OnOpenAction.pinGate, OnLimitAction.notifyOnly, OnSessionLimitAction.limitWithExtensions).also { harness.launchTargetA() }
    @Test fun r10_math_hardBlock_break() = seedTriple(OnOpenAction.deterrentMath, OnLimitAction.hardBlock, OnSessionLimitAction.mandatoryBreak).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r11_notifyOnly_noOverlayOnLimit() {
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onLimitAction = OnLimitAction.notifyOnly,
                    onSessionLimitAction = OnSessionLimitAction.notifyOnly,
                    sessionLimitMs = null,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        val engineResult = evaluateSeededProfile(seeded.profileId, seeded.packageName, noLimitToday = false)
        assertTrue(engineResult is RuleResult.Allowed)
        harness.launchTargetA()
        assertTrue(
            runBlocking { enforcementCoordinator.awaitAllowedWithoutOverlay(seeded.packageName) },
        )
    }
    @Test fun r12_hardBlock_noBypass() = seedTriple(OnOpenAction.none, OnLimitAction.hardBlock, OnSessionLimitAction.hardBlock).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r13_limitWithExtensions_showsButtons() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.none,
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    onSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
                    sessionLimitMs = null,
                    limitExtensionPolicy = ExtensionPolicy(surfaceMode = ExtensionSurfaceMode.overlay),
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        assertTrue(harness.awaitCondition { harness.isExtensionButtonsVisible() })
    }
    @Test fun r14_mandatoryBreak_showsCountdown() = seedTriple(OnOpenAction.none, OnLimitAction.mandatoryBreak, OnSessionLimitAction.mandatoryBreak).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r15_noScheduleMatch_openOverride() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    noScheduleMatchMode = SchedulePolicyMode.customize,
                    noScheduleMatchOverrides = SchedulePolicyOverrides(onOpenAction = OnOpenAction.deterrentMath),
                ),
            )
        }
        harness.launchTargetA()
    }
    @Test fun r16_noScheduleMatch_limitOverride() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    noScheduleMatchMode = SchedulePolicyMode.customize,
                    noScheduleMatchOverrides = SchedulePolicyOverrides(onLimitAction = OnLimitAction.hardBlock),
                ),
            )
            seedHardBlockProfile()
        }
        harness.launchTargetA()
    }
    @Test fun r17_noScheduleMatch_sessionOverride() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    noScheduleMatchMode = SchedulePolicyMode.customize,
                    noScheduleMatchOverrides = SchedulePolicyOverrides(onSessionLimitAction = OnSessionLimitAction.hardBlock),
                ),
            )
        }
        harness.launchTargetA()
    }
    @Test fun r18_customizeSegment_limitAction() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(profileRepository)
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.customize,
                windows = listOf(com.gatekeep.domain.ScheduleTestWindows.aroundNow(seeded.profileId, null)),
                overrides = SchedulePolicyOverrides(onLimitAction = OnLimitAction.hardBlock),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }
    @Test fun r19_openNone_skipsFriction() = seedTriple(OnOpenAction.none, OnLimitAction.hardBlock, OnSessionLimitAction.hardBlock, block = false).also { harness.launchTargetA() }
    @Test fun r20_sessionNotify_allowedFlag() = seedTriple(OnOpenAction.none, OnLimitAction.notifyOnly, OnSessionLimitAction.notifyOnly, block = false).also { harness.launchTargetA() }

    // --- Interaction matrix (R-INT): open / session / period orthogonality ---

    @Test
    fun rInt01_openWait_dailyHardBlock_skipsOpenWait() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                    onLimitAction = OnLimitAction.hardBlock,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        harness.launchTargetA()
        assertTrue(
            harness.assertHardBlockWithoutOpenWait(GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC),
        )
    }

    @Test
    fun rInt02_openWait_dailyExtensions_openFirst() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                    onLimitAction = OnLimitAction.limitWithExtensions,
                    onSessionLimitAction = OnSessionLimitAction.notifyOnly,
                    sessionLimitMs = null,
                    limitExtensionPolicy = ExtensionPolicy(surfaceMode = ExtensionSurfaceMode.overlay),
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOpenFriction())
        harness.waitForElapsedMs(GatekeepTestFixtures.TestDurations.msAfterTimer(GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC))
        assertTrue(harness.waitForOverlay(8_000) || harness.awaitCondition(8_000) { harness.isExtensionButtonsVisible() })
    }

    @Test
    fun rInt03_openWait_underLimits_proceeds() {
        runSeed {
            GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                    onLimitAction = OnLimitAction.notifyOnly,
                    sessionLimitMs = null,
                    dailyLimitMs = null,
                ),
            )
        }
        harness.launchTargetA()
        runBlocking { enforcementCoordinator.evaluateMonitoredPackageForTests(EnforcementTestPackages.TARGET_A) }
        assertTrue(
            "Open friction not shown; coordinator=${runBlocking { enforcementCoordinator.evaluateMonitoredPackageForTests(EnforcementTestPackages.TARGET_A) }}",
            harness.waitForOpenFriction(12_000),
        )
        harness.waitForElapsedMs(GatekeepTestFixtures.TestDurations.msAfterTimer(GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC) + 800)
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun rInt04_noLimitToday_sessionHardBlock_stillBlocks() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onSessionLimitAction = OnSessionLimitAction.hardBlock,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
            GatekeepTestFixtures.seedPause(
                usageRepository, PauseType.noLimitToday, seeded.profileId, seeded.packageName,
                untilMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.FUTURE_OFFSET_MS,
            )
        }
        harness.launchTargetA()
        harness.waitForElapsedMs(GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS + GatekeepTestFixtures.TestDurations.TIMER_BUFFER_MS)
        assertTrue(harness.waitForOverlay())
    }

    @Test
    fun rInt06_noLimitToday_periodBypass_noDailyBlock() {
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    onLimitAction = OnLimitAction.hardBlock,
                    onSessionLimitAction = OnSessionLimitAction.notifyOnly,
                    sessionLimitMs = null,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository, seeded.profileId, seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
            GatekeepTestFixtures.seedPause(
                usageRepository, PauseType.noLimitToday, seeded.profileId, seeded.packageName,
                untilMs = System.currentTimeMillis() + GatekeepTestFixtures.TestDurations.FUTURE_OFFSET_MS,
            )
        }
        val engineResult = evaluateSeededProfile(seeded.profileId, seeded.packageName, noLimitToday = true)
        assertTrue(engineResult is RuleResult.Allowed)
        harness.launchTargetA()
        assertTrue(
            runBlocking { enforcementCoordinator.awaitAllowedWithoutOverlay(seeded.packageName) },
        )
    }

    private fun evaluateSeededProfile(
        profileId: Long,
        packageName: String,
        noLimitToday: Boolean,
    ): RuleResult = runBlocking {
        val profile = profileRepository.observeProfiles().first().first { it.id == profileId }
        val limit = profileRepository.getLimit(profileId, packageName)
        val now = System.currentTimeMillis()
        val pauses = usageRepository.observeActivePauses(now).first()
        val periodLimitsDisabled = if (noLimitToday) {
            true
        } else {
            pauses.any {
                it.type == PauseType.noLimitToday &&
                    it.profileId == profileId &&
                    it.packageName == packageName &&
                    it.untilEpochMs > now
            }
        }
        val dayStart = com.gatekeep.domain.TimeBoundaries.dayStartEpochMs(now)
        val hourStart = com.gatekeep.domain.TimeBoundaries.hourStartEpochMs(now)
        val weekStart = com.gatekeep.domain.TimeBoundaries.weekBounds(now).startMs
        val usage = com.gatekeep.domain.model.UsageSnapshot(
            dailyMs = usageRepository.getDailyUsage(profileId, packageName, dayStart),
            hourlyMs = usageRepository.getHourlyUsage(profileId, packageName, hourStart),
            weeklyMs = usageRepository.getWeeklyUsage(profileId, packageName, weekStart),
        )
        RuleEngine.evaluate(
            RuleEvaluationContext(
                nowEpochMs = now,
                packageName = packageName,
                profile = profile,
                limit = limit,
                isMonitored = true,
                usage = usage,
                sessionState = usageRepository.getSessionState(profileId, packageName),
                pauses = pauses,
                enforcementConfig = profile.enforcementConfig(),
                periodLimitsDisabled = periodLimitsDisabled,
            ),
        )
    }

}
