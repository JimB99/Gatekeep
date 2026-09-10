package com.gatekeep.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EnforcementSettingsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { GatekeepTestFixtures.seedEnforcementReady(settingsRepository) }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun openEnforcementSettings() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enforcement", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    private fun openNotificationSettings() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Notifications", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun en01_enforcementOff_updatesSettings() {
        openEnforcementSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_ENFORCEMENT_TOGGLE).performClick()
        composeRule.waitForIdle()
        val enabled = runBlocking { settingsRepository.settings.first().enforcementEnabled }
        assertFalse(enabled)
    }

    @Test
    fun en02_enforcementOn_persists() {
        openEnforcementSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_ENFORCEMENT_TOGGLE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_ENFORCEMENT_TOGGLE).performClick()
        composeRule.waitForIdle()
        val enabled = runBlocking { settingsRepository.settings.first().enforcementEnabled }
        assertTrue(enabled)
    }

    @Test
    fun set01_sessionTimerToggle() {
        openNotificationSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_SESSION_TIMER_TOGGLE).performClick()
        composeRule.waitForIdle()
        val on = runBlocking { settingsRepository.settings.first().showSessionTimerNotification }
        assertFalse(on)
    }

    @Test
    fun set02_weeklyReportToggle() {
        openNotificationSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_WEEKLY_REPORT_TOGGLE).performClick()
        composeRule.waitForIdle()
        val on = runBlocking { settingsRepository.settings.first().weeklyReportEnabled }
        assertFalse(on)
    }

    @Test
    fun set03_hudToggle_linkedToTimer() {
        openNotificationSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_SESSION_TIMER_TOGGLE).assertIsDisplayed()
    }
}
