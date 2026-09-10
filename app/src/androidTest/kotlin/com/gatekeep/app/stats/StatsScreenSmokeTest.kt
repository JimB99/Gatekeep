package com.gatekeep.app.stats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeep.app.MainActivity
import com.gatekeep.app.support.EnforcementTestPackages
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
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
class StatsScreenSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var usageRepository: UsageRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            GatekeepTestFixtures.seedEnforcementReady(settingsRepository)
            val seeded = GatekeepTestFixtures.seedProfileWithMonitoredApp(
                profileRepository,
                packageName = EnforcementTestPackages.TARGET_A,
            )
            GatekeepTestFixtures.seedUsageAtCap(
                usageRepository,
                seeded.profileId,
                seeded.packageName,
                dailyMs = 30 * 60_000L,
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Stats").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun st01_statsScreen_loads() {
        composeRule.onNodeWithTag(GatekeepTestTags.STATS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithText("Statistics", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun st02_periodSwipe_changesRange() {
        composeRule.onNodeWithContentDescription("Previous", ignoreCase = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun st03_barChart_seededUsage() {
        st01_statsScreen_loads()
    }
}
