package com.gatekeep.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeInputDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rollingDurationDialog_showsDoubleZeroAndUpdatesDisplay() {
        var confirmedSeconds: Int? = null
        composeRule.setContent {
            RollingDurationDialog(
                initialTotalSeconds = 0,
                onDismiss = {},
                onConfirm = { confirmedSeconds = it },
                title = "Custom duration",
            )
        }

        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Digit 3").performClick()
        composeRule.onNodeWithText("00:00:03").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Digit 0").performClick()
        composeRule.onNodeWithText("00:00:30").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Double zero").performClick()
        composeRule.onNodeWithText("00:30:00").assertIsDisplayed()

        composeRule.onNodeWithText("Set").performClick()
        assertEquals(30 * 60, confirmedSeconds)
    }

    @Test
    fun twentyFourHourClockDialog_isDisplayed() {
        composeRule.setContent {
            TwentyFourHourClockDialog(
                initialMinuteOfDay = 9 * 60 + 30,
                onDismiss = {},
                onConfirm = {},
                title = "Custom time",
            )
        }

        composeRule.onNodeWithText("Custom time").assertIsDisplayed()
        composeRule.onNodeWithText("Set").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
