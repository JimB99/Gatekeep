package com.gatekeep.app.profiles

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.domain.model.OnOpenAction
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProfilePinScreenTest {

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
        openProfilePin()
    }

    private fun openProfilePin() {
        composeRule.onNodeWithText("Test Profile", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Profile PIN", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun pp05_enableProfilePin_inEditor() {
        composeRule.onNodeWithTag(GatekeepTestTags.PROFILE_PIN_FIELD).performTextInput(GatekeepTestFixtures.PROFILE_PIN)
        composeRule.onNodeWithTag(GatekeepTestTags.PROFILE_PIN_SAVE).performClick()
        composeRule.waitForIdle()
        val profile = runBlocking { profileRepository.observeProfiles().first().first { it.id == profileId } }
        assertNotNull(profile.passwordHash)
    }

    @Test
    fun pp06_pinGateConfigured() {
        runBlocking {
            val profile = profileRepository.observeProfiles().first().first { it.id == profileId }
            profileRepository.updateProfile(profile.copy(onOpenAction = OnOpenAction.pinGate))
        }
        val updated = runBlocking { profileRepository.observeProfiles().first().first { it.id == profileId } }
        assertEquals(OnOpenAction.pinGate, updated.onOpenAction)
    }
}
