package com.gatekeep.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.E2eGate
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.support.GatekeepUiNav
import com.gatekeep.app.support.GatekeepUiNav.openSettings
import com.gatekeep.app.enforcement.EnforcementTraceQuery
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        composeRule.openSettings()
        composeRule.onNodeWithText("Enforcement", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    private fun openNotificationSettings() {
        composeRule.openSettings()
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
    @E2eGate
    fun set01_sessionTimerToggle() {
        openNotificationSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_SESSION_TIMER_TOGGLE).performClick()
        composeRule.waitForIdle()
        val on = runBlocking { settingsRepository.settings.first().showSessionTimerNotification }
        assertFalse(
            "session timer still on after toggle (leftover overlay can eat the click)",
            on,
        )
    }

    @Test
    fun set02_weeklyReportToggle() {
        val initial = runBlocking { settingsRepository.settings.first().weeklyReportEnabled }
        openNotificationSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_WEEKLY_REPORT_TOGGLE).performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { settingsRepository.settings.first().weeklyReportEnabled } != initial
        }
        val after = runBlocking { settingsRepository.settings.first().weeklyReportEnabled }
        assertTrue(initial != after)
    }

    @Test
    fun set03_hudToggle_linkedToTimer() {
        openNotificationSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_SESSION_TIMER_TOGGLE).assertIsDisplayed()
    }

    @Test
    fun set04_aboutIsLast_logRangeChipsVisible() {
        composeRule.openSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_PERMISSIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_ABOUT).assertIsDisplayed()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_ABOUT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Copy enforcement log", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(
            GatekeepTestTags.enforcementLogRange(EnforcementTraceQuery.Window.LAST_HOUR.name),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            GatekeepTestTags.enforcementLogRange(EnforcementTraceQuery.Window.ALL.name),
        ).assertIsDisplayed()
    }

    @Test
    fun dayResetSetting_defaultsToFourAmAndIsVisible() {
        val reset = runBlocking { settingsRepository.settings.first().dayResetMinuteOfDay }
        assertTrue(reset == 4 * 60)
        openEnforcementSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_DAY_RESET).assertIsDisplayed()
        composeRule.onNodeWithText("Day starts at", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun sessionContinuityGrace_defaultsToOneMinuteAndIsVisible() {
        val grace = runBlocking { settingsRepository.settings.first().sessionContinuityGraceMs }
        assertEquals(60_000L, grace)
        openEnforcementSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_SESSION_CONTINUITY_GRACE)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Session continuity grace", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "How long you have to leave before a new session starts",
            substring = true,
            ignoreCase = true,
        ).assertIsDisplayed()
    }

    @Test
    fun sessionContinuityGrace_customValue_persistsAndIsShown() {
        runBlocking {
            settingsRepository.updateSettings { it.copy(sessionContinuityGraceMs = 15_000L) }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        val grace = runBlocking { settingsRepository.settings.first().sessionContinuityGraceMs }
        assertEquals(15_000L, grace)
        openEnforcementSettings()
        composeRule.onNodeWithText("15s", substring = true, ignoreCase = true).assertIsDisplayed()
    }
}
