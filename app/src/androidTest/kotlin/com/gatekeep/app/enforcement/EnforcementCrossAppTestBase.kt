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
        harness.onNavigatedAway = null
        harness.onTargetLaunched = null
        runCatching { harness.restoreStayAwake() }
        runCatching { harness.wakeDevice() }
        runCatching {
            runBlocking { enforcementCoordinator.resetInstrumentationState() }
        }
    }

    @Before
    override fun baseSetUp() {
        super.baseSetUp()
        if (!harness.isAccessibilityHealthy()) {
            GatekeepInstrumentedTestBase.resetEnforcementEnvironmentCache()
        }
        enforcementCoordinator.resetInstrumentationState()
        harness.resetTestTargetUsageStats()
        harness.wakeDevice()
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
            enforcementCoordinator.pollForeground()
            enforcementCoordinator.refresh()
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
        assertTrue(runBlocking { enforcementCoordinator.awaitOverlayHiddenForTests() })
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
