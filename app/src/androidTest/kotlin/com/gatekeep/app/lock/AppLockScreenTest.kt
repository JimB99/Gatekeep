package com.gatekeep.app.lock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.lock.AppLockScreen
import com.gatekeep.app.ui.lock.AppLockTestTags
import com.gatekeep.app.util.PasswordHasher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pin02_correctPin_unlocks() {
        var unlocked = false
        composeRule.setContent {
            AppLockScreen(
                passwordHash = PasswordHasher.hash(GatekeepTestFixtures.TEST_PIN),
                onUnlocked = { unlocked = true },
            )
        }

        composeRule.onNodeWithTag(AppLockTestTags.PIN_FIELD).performTextInput(GatekeepTestFixtures.TEST_PIN)
        composeRule.onNodeWithTag(AppLockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitForIdle()
        assert(unlocked)
    }

    @Test
    fun pin03_wrongPin_staysLocked() {
        var unlocked = false
        composeRule.setContent {
            AppLockScreen(
                passwordHash = PasswordHasher.hash(GatekeepTestFixtures.TEST_PIN),
                onUnlocked = { unlocked = true },
            )
        }

        composeRule.onNodeWithTag(AppLockTestTags.PIN_FIELD).performTextInput("0000")
        composeRule.onNodeWithTag(AppLockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitForIdle()
        assert(!unlocked)
        composeRule.onNodeWithText("Enter PIN to unlock").assertIsDisplayed()
    }
}
