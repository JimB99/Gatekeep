package com.gatekeep.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gatekeep.app.ui.apps.AppPickerScreen
import com.gatekeep.app.ui.home.ProfilesHomeScreen
import com.gatekeep.app.ui.onboarding.OnboardingScreen
import com.gatekeep.app.ui.pause.PauseScreen
import com.gatekeep.app.ui.profiles.ProfileDetailScreen
import com.gatekeep.app.ui.schedule.ScheduleEditorScreen
import com.gatekeep.app.ui.settings.SettingsScreen
import com.gatekeep.app.ui.stats.StatsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val PROFILE_DETAIL = "profile/{profileId}"
    const val APP_PICKER = "apps/{profileId}"
    const val SCHEDULE = "schedule/{profileId}"
    const val STATS = "stats"
    const val PAUSE = "pause"
    const val SETTINGS = "settings"

    fun profileDetail(id: Long) = "profile/$id"
    fun appPicker(profileId: Long) = "apps/$profileId"
    fun schedule(profileId: Long) = "schedule/$profileId"
}

@Composable
fun GatekeepNavHost(
    startDestination: String,
    onEnforcementStart: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    onEnforcementStart()
                    navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.DASHBOARD) {
            ProfilesHomeScreen(
                onNavigateStats = { navController.navigate(Routes.STATS) },
                onNavigatePause = { navController.navigate(Routes.PAUSE) },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateApps = { profileId -> navController.navigate(Routes.appPicker(profileId)) },
                onNavigateSchedule = { profileId -> navController.navigate(Routes.schedule(profileId)) },
                onNavigateProfileDetail = { id -> navController.navigate(Routes.profileDetail(id)) },
            )
        }
        composable(
            route = Routes.PROFILE_DETAIL,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            ProfileDetailScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onEditApps = { navController.navigate(Routes.appPicker(profileId)) },
                onEditSchedule = { navController.navigate(Routes.schedule(profileId)) },
            )
        }
        composable(
            route = Routes.APP_PICKER,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            AppPickerScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.SCHEDULE,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            ScheduleEditorScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PAUSE) {
            PauseScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onReplayOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }
    }
}
