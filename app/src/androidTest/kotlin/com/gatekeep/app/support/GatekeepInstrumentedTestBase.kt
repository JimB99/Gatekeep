package com.gatekeep.app.support

import com.gatekeep.app.enforcement.EnforcementCoordinator
import com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService
import com.gatekeep.app.util.PermissionHelper
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule

abstract class GatekeepInstrumentedTestBase {

    companion object {
        @Volatile
        private var enforcementEnvironmentPrepared = false

        fun resetEnforcementEnvironmentCache() {
            enforcementEnvironmentPrepared = false
        }
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var enforcementCoordinator: EnforcementCoordinator

    protected val harness = EnforcementTestHarness()

    @Before
    open fun baseSetUp() {
        hiltRule.inject()
        runBlocking {
            GatekeepTestFixtures.resetInstrumentedUiState(settingsRepository, profileRepository)
        }
    }

    protected fun runSeed(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    protected fun prepareEnforcementEnvironment() {
        val accessibilityHealthy = harness.isAccessibilityHealthy()
        if (enforcementEnvironmentPrepared &&
            harness.hasCoreEnforcementPermissionsGranted() &&
            accessibilityHealthy
        ) {
            enforcementCoordinator.ensureForegroundMonitoring()
            enforcementCoordinator.refresh()
            return
        }
        if (!accessibilityHealthy) {
            resetEnforcementEnvironmentCache()
        }
        harness.grantEnforcementPermissionsForCrossAppTests()
        if (!harness.hasCoreEnforcementPermissionsGranted()) {
            org.junit.Assume.assumeTrue(
                "Skipping: core enforcement permissions not granted (${harness.permissionDiagnostics()}).",
                false,
            )
        }
        enforcementCoordinator.ensureForegroundMonitoring()
        enforcementEnvironmentPrepared = true
        enforcementCoordinator.refresh()
    }

    protected fun seedBlockedProfile(
        packageName: String = EnforcementTestPackages.TARGET_A,
        config: GatekeepTestFixtures.ProfileSeedConfig = GatekeepTestFixtures.ProfileSeedConfig(),
        dailyMs: Long = config.dailyLimitMs ?: GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
    ): GatekeepTestFixtures.SeededProfile {
        return runBlocking {
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository = profileRepository,
                packageName = packageName,
                config = config,
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository = usageRepository,
                profileId = seeded.profileId,
                packageName = packageName,
                dailyMs = dailyMs,
            )
            seeded
        }
    }
}
