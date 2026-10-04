package com.gatekeep.app.enforcement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.RuleResult
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
        assertTrue(harness.waitForOverlay())
        assertTrue(harness.clickOverlayExtensionMinutes(5))
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
    fun e11_overDailyCap_programmaticGrace_evaluatesAllowed() {
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = seedBlockedProfile(
                config = extensionOverlayConfig(),
                dailyMs = GatekeepTestFixtures.TestDurations.OVER_DAILY_CAP_MS,
            )
            val granted = enforcementCoordinator.grantExtensionForProfileAwait(
                seeded.profileId,
                seeded.packageName,
                minutes = 5,
            )
            assertTrue(granted)
        }
        val result = runBlocking {
            enforcementCoordinator.evaluateMonitoredPackageForTests(seeded.packageName)
        }
        assertTrue(result is RuleResult.Allowed)
        assertOverlayHidden()
    }

    @Test
    fun e12_overDailyCap_graceCountdown_showsFiniteLimitNotInfinity() {
        lateinit var seeded: GatekeepTestFixtures.SeededProfile
        runSeed {
            seeded = seedBlockedProfile(
                config = extensionOverlayConfig(),
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
        assertAllowedWithoutBlockingOverlay()
        assertTrue(harness.waitForCountdownNotification())
        val body = harness.countdownNotificationBody()
        assertTrue("Countdown notification should include daily usage line", body?.contains("Daily") == true)
        assertFalse(
            "Grace over daily cap must not show ∞ in countdown HUD (regression: 283h / ∞)",
            harness.countdownNotificationContainsInfinity(),
        )
        val dailyUsedPart = Regex("Daily\\s+([^/]+)/").find(body ?: "")?.groupValues?.getOrNull(1)?.trim()
        val usedHours = dailyUsedPart?.let { Regex("(\\d+)h").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        assertTrue(
            "Daily HUD used must not exceed 24h (regression: inflated usage+grace display)",
            usedHours == null || usedHours <= 24,
        )
    }

    @Test
    fun e13_overlayNoLimitToday_dismissesOverlayAndStaysAllowed() {
        runSeed {
            seedBlockedProfile(config = extensionOverlayConfig())
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        assertTrue(harness.clickOverlayNoLimitToday())
        assertOverlayHidden()
        assertAllowedWithoutBlockingOverlay()
    }

    @Test
    fun e14_overlayNoLimitToday_sessionPolicyUsedWhenLimitPolicyDisables() {
        runSeed {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                config = GatekeepTestFixtures.ProfileSeedConfig(
                    dailyLimitMs = null,
                    onSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
                    sessionLimitMs = GatekeepTestFixtures.TestDurations.SESSION_LIMIT_MS,
                    sessionExtensionPolicy = ExtensionPolicy(
                        optionMinutes = listOf(1, 5, 10),
                        surfaceMode = ExtensionSurfaceMode.overlay,
                        showNoLimitToday = true,
                    ),
                    limitExtensionPolicy = ExtensionPolicy(
                        optionMinutes = listOf(1, 5, 10),
                        surfaceMode = ExtensionSurfaceMode.overlay,
                        showNoLimitToday = false,
                    ),
                ),
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                sessionState = com.gatekeep.app.support.GatekeepTestFixtures.continuedSession(
                    seeded.packageName,
                    GatekeepTestFixtures.TestDurations.SESSION_OVER_CAP_MS,
                ),
            )
        }
        harness.launchTargetA()
        assertTrue(harness.waitForOverlay())
        assertTrue(harness.clickOverlayNoLimitToday())
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
        assertTrue(harness.waitForOpenFriction())
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
