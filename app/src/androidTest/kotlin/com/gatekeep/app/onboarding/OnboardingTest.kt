package com.gatekeep.app.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.GatekeepTestTags
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
        recreateWithSeed {
            settingsRepository.updateSettings { it.copy(onboardingComplete = false) }
        }
        composeRule.onNodeWithTag(GatekeepTestTags.ONBOARDING_GET_STARTED).assertIsNotEnabled()
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
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
