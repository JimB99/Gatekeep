package com.gatekeep.app.lock

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.lock.AppLockTestTags
import com.gatekeep.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityAppLockTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun recreateWithSeededSettings(seed: suspend () -> Unit) {
        runBlocking {
            seed()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun assertLockScreenVisible() {
        composeRule.onNodeWithText("Enter PIN to unlock").assertIsDisplayed()
        composeRule.onNodeWithTag(AppLockTestTags.PIN_FIELD).assertIsDisplayed()
    }

    private fun assertDashboardVisible() {
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Enter PIN to unlock").assertIsNotDisplayed()
    }

    private fun unlockApp(pin: String = GatekeepTestFixtures.TEST_PIN) {
        composeRule.onNodeWithTag(AppLockTestTags.PIN_FIELD).performTextInput(pin)
        composeRule.onNodeWithTag(AppLockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun pin01_coldStart_showsLockScreen() {
        recreateWithSeededSettings {
            GatekeepTestFixtures.seedAppLockEnabled(settingsRepository)
        }
        assertLockScreenVisible()
    }

    @Test
    fun pin02_correctPin_showsDashboard() {
        recreateWithSeededSettings {
            GatekeepTestFixtures.seedAppLockEnabled(settingsRepository)
        }
        unlockApp()
        assertDashboardVisible()
    }

    @Test
    fun pin04_backgroundingRequiresPinAgain() {
        recreateWithSeededSettings {
            GatekeepTestFixtures.seedAppLockEnabled(settingsRepository)
        }
        unlockApp()
        assertDashboardVisible()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertLockScreenVisible()
    }

    @Test
    fun pin07_rotation_staysUnlocked() {
        recreateWithSeededSettings {
            GatekeepTestFixtures.seedAppLockEnabled(settingsRepository)
        }
        unlockApp()
        assertDashboardVisible()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        assertDashboardVisible()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test
    fun pin08_lockDisabled_skipsLockScreen() {
        recreateWithSeededSettings {
            GatekeepTestFixtures.seedAppLockDisabled(settingsRepository)
        }
        assertDashboardVisible()
    }

    @Test
    fun pin09_noPin_skipsLockScreen() {
        recreateWithSeededSettings {
            GatekeepTestFixtures.seedNoPin(settingsRepository)
        }
        assertDashboardVisible()
    }

    @Ignore("am force-stop kills the in-process instrumentation host; verify PIN-06 manually")
    @Test
    fun pin06_forceStop_requiresPinOnRelaunch() {
        // Manual: force-stop from Settings, relaunch app, confirm PIN screen appears.
    }

    @Test
    fun pin10_rapidBackgroundResume_requiresPin() {
        recreateWithSeededSettings {
            GatekeepTestFixtures.seedAppLockEnabled(settingsRepository)
        }
        unlockApp()
        repeat(3) {
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForIdle()
            assertLockScreenVisible()
            unlockApp()
            assertDashboardVisible()
        }
    }
}
