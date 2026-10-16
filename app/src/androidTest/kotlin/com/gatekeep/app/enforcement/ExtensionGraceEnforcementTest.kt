package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.RuleResult
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for extension grace enforcement, overlay dismiss after grant,
 * and countdown HUD cap display (finite limit, not ∞) when usage exceeds the daily cap.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class ExtensionGraceEnforcementTest : EnforcementCrossAppTestBase() {

    private fun extensionOverlayConfig() = GatekeepTestFixtures.ProfileSeedConfig(
        onLimitAction = OnLimitAction.limitWithExtensions,
        limitExtensionPolicy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            surfaceMode = ExtensionSurfaceMode.overlay,
            showNoLimitToday = true,
        ),
    )

    @Test
    fun e10_overlayExtend5_overDailyCap_dismissesOverlayAndStaysAllowed() {
        runSeed {
            seedBlockedProfile(config = extensionOverlayConfig())
        }
        harness.launchTargetA()
        assertTrue("e10 overlay ${harness.screenDiagnostics()}", harness.waitForOverlay())
        assertTrue(
            "e10 +5 click and overlay gone ${harness.screenDiagnostics()}",
            harness.clickOverlayExtensionMinutes(5),
        )
        assertOverlayHidden()
        assertAllowedWithoutBlockingOverlay()
        repeat(3) {
            val result = runBlocking {
                enforcementCoordinator.evaluateMonitoredPackageForTests(EnforcementTestPackages.TARGET_A)
            }
            assertTrue("Re-evaluation $it should stay allowed", result is RuleResult.Allowed)
        }
        assertOverlayHidden()
    }

    @Test
    fun e13_overlayNoLimitToday_dismissesOverlayAndStaysAllowed() {
        runSeed {
            seedBlockedProfile(config = extensionOverlayConfig())
        }
        harness.launchTargetA()
        assertTrue("e13 overlay ${harness.screenDiagnostics()}", harness.waitForOverlay())
        assertTrue(
            "e13 no-limit click and overlay gone ${harness.screenDiagnostics()}",
            harness.clickOverlayNoLimitToday(),
        )
        assertOverlayHidden()
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun e15_openWait_afterExistingGrace_doesNotAskForAnotherExtension() {
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = seedBlockedProfile(
                config = extensionOverlayConfig().copy(
                    onOpenAction = OnOpenAction.deterrentWait,
                    openWaitDurationSeconds = GatekeepTestFixtures.TestDurations.CANCELLED_OPEN_WAIT_SEC,
                    sessionLimitMs = 20 * 60_000L,
                    dailyLimitMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
                ),
                dailyMs = GatekeepTestFixtures.TestDurations.OVER_DAILY_CAP_MS,
            )
            assertTrue(
                enforcementCoordinator.grantExtensionForProfileAwait(
                    seeded.profileId,
                    seeded.packageName,
                    minutes = 5,
                ),
            )
        }
        harness.launchTargetA()
        val traces = enforcementCoordinator.enforcementTraceSnapshot()
        assertTrue(
            "e15 open wait missing traces=$traces ${harness.screenDiagnostics()}",
            harness.waitForOpenFriction() ||
                traces.any {
                    it.event == EnforcementTrace.SHOW_OPEN_DETERRENT ||
                        (it.event == EnforcementTrace.OVERLAY_SHOW && it.fields["openGate"] == "true")
                },
        )
        runBlocking {
            enforcementCoordinator.onOpenGatePassed(seeded.packageName)
        }
        assertAllowedWithoutBlockingOverlay()
        assertOverlayHidden()
        assertTrue(
            "extension overlay must not appear after open-wait when grace remains",
            !harness.isExtensionButtonsVisible(),
        )
        val result = runBlocking {
            enforcementCoordinator.evaluateMonitoredPackageForTests(seeded.packageName)
        }
        assertTrue("post-wait evaluation should stay allowed", result is RuleResult.Allowed)
        assertTrue(!harness.isExtensionButtonsVisible())
    }
}
