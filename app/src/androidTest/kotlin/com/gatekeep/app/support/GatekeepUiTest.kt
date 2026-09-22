package com.gatekeep.app.support

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.gatekeep.app.MainActivity
import com.gatekeep.app.ui.GatekeepTestTags

object GatekeepUiTest {

    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openSettings() {
        onNodeWithTag(GatekeepTestTags.NAV_SETTINGS).performClick()
        waitForIdle()
    }

    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openPermissions() {
        openSettings()
        onNodeWithTag(GatekeepTestTags.SETTINGS_PERMISSIONS).performClick()
        waitForIdle()
    }

    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openStats() {
        onNodeWithTag(GatekeepTestTags.NAV_STATS).performClick()
        waitForIdle()
    }

    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openPause() {
        onNodeWithTag(GatekeepTestTags.NAV_PAUSE).performClick()
        waitForIdle()
    }

    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openProfile(name: String) {
        ensureDashboardVisible()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(GatekeepTestTags.profileCard(name)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(GatekeepTestTags.profileCard(name)).performClick()
        waitForIdle()
    }

    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.ensureDashboardVisible() {
        repeat(3) {
            if (onAllNodesWithTag(GatekeepTestTags.DASHBOARD_ROOT).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            runCatching {
                onNodeWithContentDescription("Back", ignoreCase = true).performClick()
            }
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(GatekeepTestTags.DASHBOARD_ROOT).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openCurrentUsage(
        profileName: String = "Test Profile",
    ) {
        openProfile(profileName)
        onNodeWithText("Current usage", substring = true, ignoreCase = true).performClick()
        waitForIdle()
    }
}
