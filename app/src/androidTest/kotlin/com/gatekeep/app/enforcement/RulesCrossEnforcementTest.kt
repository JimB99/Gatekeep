package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidTest
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
    @Test fun r18_customizeSegment_limitAction() {
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(profileRepository)
            GatekeepTestFixtures.seedScheduleSegment(
                profileRepository, seeded.profileId, SchedulePolicyMode.customize,
                windows = listOf(com.gatekeep.domain.ScheduleTestWindows.aroundNow(seeded.profileId, null)),
                overrides = SchedulePolicyOverrides(
                    onLimitAction = OnLimitAction.hardBlock,
                    dailyLimitMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
            )
        }
        harness.wakeDevice()
        harness.launchTargetA()
        val eval = evaluateSeededProfile(seeded.profileId, seeded.packageName, noLimitToday = false)
        val coord = runBlocking {
            enforcementCoordinator.evaluateMonitoredPackageForTests(seeded.packageName)
        }
        assertTrue(
            "r18 overlay eval=$eval coord=$coord ${harness.screenDiagnostics()}",
            harness.waitForOverlay(),
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
        val shownOpen = harness.isFrictionVisible() || harness.isOpenWaitVisible() ||
            harness.waitForOpenFriction(2_000)
        val coord = enforcementCoordinator.state.value
        val traces = enforcementCoordinator.enforcementTraceSnapshot()
        val openIdx = traces.indexOfFirst {
            it.event == EnforcementTrace.SHOW_OPEN_DETERRENT ||
                (it.event == EnforcementTrace.OVERLAY_SHOW && it.fields["openGate"] == "true")
        }
        val dailyIdx = traces.indexOfFirst {
            it.event == EnforcementTrace.OVERLAY_SHOW && it.fields["reason"] == "dailyLimit"
        }
        assertTrue(
            "open wait missing coord=$coord traces=$traces ${harness.screenDiagnostics()}",
            shownOpen || openIdx >= 0,
        )
        assertTrue(
            "open wait must precede daily overlay coord=$coord traces=$traces ${harness.screenDiagnostics()}",
            dailyIdx < 0 || openIdx < dailyIdx,
        )
        if (shownOpen) {
            harness.waitForElapsedMs(
                GatekeepTestFixtures.TestDurations.msAfterTimer(
                    GatekeepTestFixtures.TestDurations.OPEN_WAIT_SEC,
                ),
            )
        }
        assertTrue(
            "limit overlay missing after open wait coord=$coord ${harness.screenDiagnostics()}",
            harness.waitForOverlay(8_000) ||
                harness.awaitCondition(8_000) { harness.isExtensionButtonsVisible() },
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
        val reset = com.gatekeep.domain.DayReset.DEFAULT_MINUTE_OF_DAY
        val dayStart = com.gatekeep.domain.TimeBoundaries.dayStartEpochMs(now, dayResetMinuteOfDay = reset)
        val hourStart = com.gatekeep.domain.TimeBoundaries.hourStartEpochMs(now)
        val weekStart = com.gatekeep.domain.TimeBoundaries.weekBounds(now, dayResetMinuteOfDay = reset).startMs
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
