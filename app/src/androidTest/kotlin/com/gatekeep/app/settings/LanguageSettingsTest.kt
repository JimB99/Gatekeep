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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LanguageSettingsTest {

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
        openLanguageSettings()
    }

    private fun openLanguageSettings() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Language", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun l01_defaultLanguageTag() {
        val tag = runBlocking { settingsRepository.settings.first().languageTag }
        assertEquals("en-GB", tag)
    }

    @Test
    fun l02_switchToEachSupportedLocale() {
        listOf("en-GB", "de-AT", "es-ES").forEach { tag ->
            composeRule.onNodeWithTag(GatekeepTestTags.LANGUAGE_OPTION_PREFIX + tag).performClick()
            composeRule.waitForIdle()
            val saved = runBlocking { settingsRepository.settings.first().languageTag }
            assertEquals(tag, saved)
        }
    }

    @Test
    fun l03_languagePersistsAfterRecreate() {
        composeRule.onNodeWithTag(GatekeepTestTags.LANGUAGE_OPTION_PREFIX + "de-AT").performClick()
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        val saved = runBlocking { settingsRepository.settings.first().languageTag }
        assertEquals("de-AT", saved)
    }

    @Test
    fun l04_localeListShowsNativeLabels() {
        composeRule.onNodeWithTag(GatekeepTestTags.LANGUAGE_OPTION_PREFIX + "de-AT").assertIsDisplayed()
        composeRule.onNodeWithTag(GatekeepTestTags.LANGUAGE_OPTION_PREFIX + "es-ES").assertIsDisplayed()
    }
}
