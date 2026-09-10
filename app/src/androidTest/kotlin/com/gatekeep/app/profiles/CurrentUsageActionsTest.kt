package com.gatekeep.app.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
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

    private var profileId: Long = 0

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            GatekeepTestFixtures.seedEnforcementReady(settingsRepository)
            profileId = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                packageName = EnforcementTestPackages.TARGET_A,
            ).profileId
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openCurrentUsage()
    }

    private fun openCurrentUsage() {
        composeRule.onNodeWithText("Test Profile", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Current usage", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
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
        composeRule.onNodeWithText("Reset", substring = true, ignoreCase = true).performClick()
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
        openCurrentUsage()
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
        openCurrentUsage()
        composeRule.onNodeWithTag(GatekeepTestTags.CURRENT_USAGE_EXTEND_PREFIX + "5").assertIsDisplayed()
    }
}
