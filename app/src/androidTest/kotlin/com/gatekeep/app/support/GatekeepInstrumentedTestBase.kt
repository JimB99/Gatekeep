package com.gatekeep.app.support

import com.gatekeep.app.enforcement.EnforcementCoordinator
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule

abstract class GatekeepInstrumentedTestBase {

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
            GatekeepTestFixtures.seedEnforcementReady(settingsRepository)
        }
    }

    protected fun runSeed(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    protected fun prepareEnforcementEnvironment() {
        harness.grantEnforcementPermissions()
        require(harness.waitForAccessibilityConnected()) {
            "Enforcement permissions not ready. Grant usage, overlay, and accessibility on the emulator."
        }
        enforcementCoordinator.refresh()
    }

    protected fun seedBlockedProfile(
        packageName: String = EnforcementTestPackages.TARGET_A,
        config: GatekeepTestFixtures.ProfileSeedConfig = GatekeepTestFixtures.ProfileSeedConfig(),
        dailyMs: Long = config.dailyLimitMs ?: 60 * 60_000L,
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
