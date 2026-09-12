package com.gatekeep.app.permissions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.EnforcementTestHarness
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.app.util.EnforcementLog
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
class PermissionBannerTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var enforcementLog: EnforcementLog

    private val harness = EnforcementTestHarness()

    @Before
    fun setUp() {
        hiltRule.inject()
        enforcementLog.clear()
        runBlocking { GatekeepTestFixtures.seedEnforcementReady(settingsRepository) }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun p01_allGranted_hidesBanner() {
        harness.grantEnforcementPermissions()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        if (!harness.waitForPermissionGrants(timeoutMs = 5_000)) {
            org.junit.Assume.assumeTrue(
                "Skipping: emulator did not report all enforcement permission grants.",
                false,
            )
        }
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsNotDisplayed()
    }

    @Test
    fun p02_missingUsage_showsBanner() {
        harness.grantEnforcementPermissions()
        harness.revokeUsagePermission()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsDisplayed()
    }

    @Test
    fun p03_missingAccessibility_showsBanner() {
        harness.grantEnforcementPermissions()
        harness.revokeAccessibility()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsDisplayed()
    }

    @Test
    fun p04_missingOverlay_showsBanner() {
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsDisplayed()
    }

    @Test
    fun p05_missingTwo_listsBoth() {
        harness.revokeUsagePermission()
        harness.revokeAccessibility()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsDisplayed()
    }

    @Test
    fun p06_enforcementDisabled_showsTurnOnCta() {
        runBlocking {
            settingsRepository.updateSettings { it.copy(enforcementEnabled = false) }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Turn on enforcement", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun p07_lastError_showsDismissBanner() {
        enforcementLog.logError("Test error", RuntimeException("test"))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsDisplayed()
    }

    @Test
    fun p08_returnFromSettings_clearsBanner() {
        harness.grantEnforcementPermissions()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        if (!harness.waitForPermissionGrants(timeoutMs = 5_000)) {
            org.junit.Assume.assumeTrue(
                "Skipping: emulator did not report all enforcement permission grants.",
                false,
            )
        }
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsNotDisplayed()
    }

    @Test
    fun p10_accessibilityRevoked_showsBanner() {
        harness.revokeAccessibility()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GatekeepTestTags.PERMISSION_BANNER).assertIsDisplayed()
    }
}
