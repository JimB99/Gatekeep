package com.gatekeep.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.support.GatekeepUiTest
import com.gatekeep.app.support.GatekeepUiTest.openSettings
import com.gatekeep.app.util.LocaleController
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
        runBlocking { GatekeepTestFixtures.seedEnforcementReady(settingsRepository, onboardingComplete = true) }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            LocaleController.apply("en-GB")
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openLanguageSettings()
    }

    private fun openLanguageSettings() {
        composeRule.openSettings()
        composeRule.onNodeWithTag(GatekeepTestTags.SETTINGS_LANGUAGE).performClick()
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
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking { settingsRepository.settings.first().languageTag } == tag
            }
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
