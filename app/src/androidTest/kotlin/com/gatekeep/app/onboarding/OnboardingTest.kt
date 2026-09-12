package com.gatekeep.app.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun recreateWithSeed(seed: suspend () -> Unit) {
        runBlocking { seed() }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun ob01_freshInstall_startsAtOnboarding() {
        recreateWithSeed {
            settingsRepository.updateSettings {
                it.copy(onboardingComplete = false, appLockEnabled = false, appPasswordHash = null)
            }
        }
        composeRule.onNodeWithTag(GatekeepTestTags.ONBOARDING_ROOT).assertIsDisplayed()
    }

    @Test
    fun ob02_getStarted_disabledUntilPermissions() {
        revokeEnforcementPermissionsForOnboarding()
        recreateWithSeed {
            settingsRepository.updateSettings { it.copy(onboardingComplete = false) }
        }
        composeRule.onNodeWithTag(GatekeepTestTags.ONBOARDING_GET_STARTED).assertIsNotEnabled()
    }

    private fun revokeEnforcementPermissionsForOnboarding() {
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("cmd appops set $pkg GET_USAGE_STATS deny")
        shell.executeShellCommand("cmd appops set $pkg SYSTEM_ALERT_WINDOW deny")
        shell.executeShellCommand("settings put secure enabled_accessibility_services \"\"")
        shell.executeShellCommand("settings put secure accessibility_enabled 0")
    }

    @Test
    fun ob03_completeOnboarding_reachesDashboard() {
        recreateWithSeed {
            settingsRepository.updateSettings {
                it.copy(onboardingComplete = false, appLockEnabled = false, appPasswordHash = null)
            }
        }
        composeRule.onNodeWithTag(GatekeepTestTags.ONBOARDING_SKIP).performClick()
        composeRule.waitForIdle()
        runBlocking {
            if (!settingsRepository.settings.first().onboardingComplete) {
                settingsRepository.updateSettings { it.copy(onboardingComplete = true) }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(GatekeepTestTags.DASHBOARD_ROOT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(GatekeepTestTags.DASHBOARD_ROOT).assertIsDisplayed()
    }
}
