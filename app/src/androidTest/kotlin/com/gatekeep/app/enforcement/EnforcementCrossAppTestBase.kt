package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.GatekeepInstrumentedTestBase
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnLimitAction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before

@LargeTest
abstract class EnforcementCrossAppTestBase : GatekeepInstrumentedTestBase() {

    @After
    fun tearDownEnforcement() {
        runCatching { harness.failIfAnr("tearDown") }
        runCatching { harness.pressHome() }
        runCatching { enforcementCoordinator.resetInstrumentationState() }
        // Hide claim is 1.5s. A leftover session-wait overlay is suite pollution
        // (obs02 isolation-green; suite dump was "Leave for at least 1m 0s").
        if (!harness.waitForOverlayGone(1_500)) {
            runCatching { enforcementCoordinator.resetInstrumentationState() }
            harness.waitForOverlayGone(1_500)
        }
        runCatching { harness.restoreStayAwake() }
        runCatching { harness.wakeDevice() }
        harness.onNavigatedAway = null
        harness.onTargetLaunched = null
    }

    @Before
    override fun baseSetUp() {
        super.baseSetUp()
        harness.failIfAnr("baseSetUp")
        if (!harness.isAccessibilityHealthy() || !harness.canReadAccessibilityWindows()) {
            GatekeepInstrumentedTestBase.resetEnforcementEnvironmentCache()
        }
        enforcementCoordinator.resetInstrumentationState()
        harness.waitForOverlayGone(1_500)
        harness.resetTestTargetUsageStats()
        harness.wakeDevice()
        harness.disableSoftImeForTests()
        restoreForegroundInjection()
        prepareEnforcementEnvironment()
    }

    /** Same-package test target cannot be told from Gatekeep UI via UsageStats alone. */
    protected fun restoreForegroundInjection() {
        harness.onTargetLaunched = { packageName, activityClassName ->
            enforcementCoordinator.onForegroundAppChanged(packageName, activityClassName)
            enforcementCoordinator.refresh()
        }
        harness.onNavigatedAway = {
            val pkg = harness.device.currentPackageName
            val leavePkg = if (pkg != null &&
                (ForegroundStabilizationPolicy.isTransientForegroundPackage(pkg) ||
                    OverlayWindowClassifier.isRecentsHost(pkg))
            ) {
                pkg
            } else {
                "com.google.android.apps.nexuslauncher"
            }
            enforcementCoordinator.injectWindowStackForTests(
                listOf(
                    OverlayWindowSnapshot(
                        packageName = leavePkg,
                        className = "android.launcher.Launcher",
                        isOverlay = false,
                        isFocused = true,
                        isActive = true,
                    ),
                ),
            )
        }
    }

    protected fun assertAllowedWithoutBlockingOverlay(
        packageName: String = EnforcementTestPackages.TARGET_A,
    ) {
        assertTrue(
            runBlocking {
                enforcementCoordinator.awaitAllowedWithoutOverlay(
                    packageName,
                    attempts = 25,
                    delayMs = 300,
                )
            },
        )
    }

    protected fun assertOverlayHidden() {
        val coordHidden = runBlocking { enforcementCoordinator.awaitOverlayHiddenForTests() }
        val uiGone = harness.waitForOverlayGone(1_500)
        assertTrue(
            "overlay still visible coordHidden=$coordHidden ${harness.screenDiagnostics()}",
            coordHidden && uiGone,
        )
    }

    protected fun assertBlockedWithOverlay(
        packageName: String = EnforcementTestPackages.TARGET_A,
    ) {
        assertTrue(runBlocking { enforcementCoordinator.awaitBlockOverlayForTests(packageName) })
    }

    protected fun seedHardBlockProfile(
        packageName: String = EnforcementTestPackages.TARGET_A,
    ) = seedBlockedProfile(
        packageName = packageName,
        config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
    )

}
