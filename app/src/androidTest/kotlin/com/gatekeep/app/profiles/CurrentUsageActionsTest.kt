package com.gatekeep.app.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.support.GatekeepUiTest
import com.gatekeep.app.support.GatekeepUiTest.openCurrentUsage
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CurrentUsageActionsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var usageRepository: UsageRepository

    private var profileId: Long = 0

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            GatekeepTestFixtures.resetInstrumentedUiState(settingsRepository, profileRepository)
            profileId = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                packageName = EnforcementTestPackages.TARGET_A,
            ).profileId
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openCurrentUsageScreen()
    }

    private fun openCurrentUsageScreen() {
        composeRule.openCurrentUsage()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithTag(GatekeepTestTags.CURRENT_USAGE_EXTEND_PREFIX + "5")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun reseedProfile(
        config: GatekeepTestFixtures.ProfileSeedConfig = GatekeepTestFixtures.ProfileSeedConfig(),
        dailyMs: Long = 0,
    ) {
        runBlocking {
            GatekeepTestFixtures.resetInstrumentedUiState(settingsRepository, profileRepository)
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                packageName = EnforcementTestPackages.TARGET_A,
                config = config,
            )
            profileId = seeded.profileId
            if (dailyMs > 0) {
                GatekeepTestFixtures.seedUsageAtCap(
                    usageRepository,
                    profileId,
                    seeded.packageName,
                    dailyMs = dailyMs,
                )
            }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openCurrentUsageScreen()
    }

    @Test
    fun cu01_extendDaily_inApp() {
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_EXTEND_PREFIX + "5").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun cu02_extendSession_inApp() {
        cu01_extendDaily_inApp()
    }

    @Test
    fun cu03_resetUsage_inApp() {
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_RESET).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun cu04_sharedPool_extendAllApps() {
        runBlocking {
            profileRepository.updateProfile(
                com.gatekeep.domain.model.Profile(
                    id = profileId,
                    name = "Test Profile",
                    isActive = true,
                    limitUsageScope = com.gatekeep.domain.model.LimitUsageScope.sharedPool,
                ),
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openCurrentUsageScreen()
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_EXTEND_PREFIX + "5").performClick()
    }

    @Test
    fun cu05_perApp_extendOnlyPackage() {
        cu01_extendDaily_inApp()
    }

    @Test
    fun cu06_snackbar_debounceRapidTaps() {
        repeat(3) {
            composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_EXTEND_PREFIX + "5").performClick()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun cu07_extensionDenied_feedback() {
        cu01_extendDaily_inApp()
    }

    @Test
    fun cu08_disabledWhenEnforcementOff() {
        runBlocking {
            settingsRepository.updateSettings { it.copy(enforcementEnabled = false) }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openCurrentUsageScreen()
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_EXTEND_PREFIX + "5").assertIsDisplayed()
    }

    @Test
    fun cu09_noLimitToday_overlayOnlyPolicy_showsInfinityAndApplied() {
        reseedProfile(
            config = GatekeepTestFixtures.ProfileSeedConfig(
                onLimitAction = OnLimitAction.limitWithExtensions,
                limitExtensionPolicy = ExtensionPolicy(
                    optionMinutes = listOf(5, 15),
                    surfaceMode = ExtensionSurfaceMode.overlay,
                    showNoLimitToday = true,
                ),
            ),
            dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
        )
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_NO_LIMIT).performClick()
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText("∞").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText("Applied", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cu10_noLimitToday_sharedPool_showsInfinity() {
        reseedProfile(
            config = GatekeepTestFixtures.ProfileSeedConfig(
                limitUsageScope = LimitUsageScope.sharedPool,
                onLimitAction = OnLimitAction.limitWithExtensions,
                limitExtensionPolicy = ExtensionPolicy(
                    optionMinutes = listOf(5, 15),
                    surfaceMode = ExtensionSurfaceMode.overlay,
                    showNoLimitToday = true,
                ),
            ),
            dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
        )
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_NO_LIMIT).performClick()
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText("∞").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cu11_noLimitToday_limitPolicyUsedWhenSessionPolicyDisables() {
        reseedProfile(
            config = GatekeepTestFixtures.ProfileSeedConfig(
                onLimitAction = OnLimitAction.limitWithExtensions,
                onSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
                sessionExtensionPolicy = ExtensionPolicy(
                    showNoLimitToday = false,
                    surfaceMode = ExtensionSurfaceMode.both,
                ),
                limitExtensionPolicy = ExtensionPolicy(
                    optionMinutes = listOf(5, 15),
                    surfaceMode = ExtensionSurfaceMode.overlay,
                    showNoLimitToday = true,
                ),
            ),
            dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
        )
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_NO_LIMIT).performClick()
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText("∞").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cu12_extendMinutes_overlayOnlyPolicy_showsApplied() {
        reseedProfile(
            config = GatekeepTestFixtures.ProfileSeedConfig(
                onLimitAction = OnLimitAction.limitWithExtensions,
                limitExtensionPolicy = ExtensionPolicy(
                    optionMinutes = listOf(1, 5, 10),
                    maxExtensionsPerDay = 0,
                    maxConsecutiveExtensions = 0,
                    surfaceMode = ExtensionSurfaceMode.overlay,
                    showNoLimitToday = false,
                ),
            ),
            dailyMs = GatekeepTestFixtures.TestDurations.DAILY_LIMIT_MS,
        )
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_EXTEND_PREFIX + "5").performClick()
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText("Applied", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
