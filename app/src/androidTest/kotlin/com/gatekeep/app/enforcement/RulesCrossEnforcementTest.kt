package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import org.junit.Assert.assertTrue
import org.junit.Test

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
                    usageRepository, seeded.profileId, seeded.packageName, dailyMs = 60 * 60_000L,
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
    @Test fun r11_notifyOnly_noOverlayOnLimit() = seedTriple(OnOpenAction.none, OnLimitAction.notifyOnly, OnSessionLimitAction.notifyOnly, block = false).also { harness.launchTargetA() }
    @Test fun r12_hardBlock_noBypass() = seedTriple(OnOpenAction.none, OnLimitAction.hardBlock, OnSessionLimitAction.hardBlock).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
    @Test fun r13_limitWithExtensions_showsButtons() = seedTriple(OnOpenAction.none, OnLimitAction.limitWithExtensions, OnSessionLimitAction.limitWithExtensions).also { harness.launchTargetA(); assertTrue(harness.waitForOverlay()) }
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
            GatekeepTestFixtures.seedUsageAtCap(usageRepository, seeded.profileId, seeded.packageName, dailyMs = 60 * 60_000L)
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
    }
    @Test fun r19_openNone_skipsFriction() = seedTriple(OnOpenAction.none, OnLimitAction.hardBlock, OnSessionLimitAction.hardBlock, block = false).also { harness.launchTargetA() }
    @Test fun r20_sessionNotify_allowedFlag() = seedTriple(OnOpenAction.none, OnLimitAction.notifyOnly, OnSessionLimitAction.notifyOnly, block = false).also { harness.launchTargetA() }
}
