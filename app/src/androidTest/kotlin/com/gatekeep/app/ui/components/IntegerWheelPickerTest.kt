package com.gatekeep.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.ui.theme.GatekeepTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegerWheelPickerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wheelDialog_confirmsSelectedValue() {
        var confirmed: Int? = null
        composeRule.setContent {
            GatekeepTheme {
                IntegerOrUnlimitedWheelDialog(
                    title = "Max per day",
                    value = 5,
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                    minValue = 1,
                    maxValue = 20,
                )
            }
        }

        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("Set").performClick()
        assertEquals(5, confirmed)
    }

    @Test
    fun wheelDialog_cancelDoesNotConfirm() {
        var confirmed: Int? = null
        composeRule.setContent {
            GatekeepTheme {
                IntegerOrUnlimitedWheelDialog(
                    title = "Max per day",
                    value = 5,
                    onDismiss = { confirmed = -1 },
                    onConfirm = { confirmed = it },
                    minValue = 1,
                    maxValue = 20,
                )
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(-1, confirmed)
    }
}
