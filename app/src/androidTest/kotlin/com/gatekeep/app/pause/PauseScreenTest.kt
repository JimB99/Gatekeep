package com.gatekeep.app.pause

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.support.GatekeepUiTest
import com.gatekeep.app.support.GatekeepUiTest.openPause
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
class PauseScreenTest {

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
        composeRule.openPause()
    }

    @Test
    fun pa01_profilePause_fromUi() {
        composeRule.onNodeWithTag(GatekeepTestTags.PAUSE_ALLOW_FIVE_MIN).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Apply", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun pa04_globalPause_fromUi() {
        // "All profiles" scope is selected by default on the pause screen.
        composeRule.onNodeWithTag(GatekeepTestTags.PAUSE_ALLOW_FIFTEEN_MIN).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Apply", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun pa11_endPauseEarly_fromUi() {
        pa01_profilePause_fromUi()
        composeRule.onNodeWithTag(GatekeepTestTags.PAUSE_RESET_ACTION).performClick()
        composeRule.waitForIdle()
    }
}
