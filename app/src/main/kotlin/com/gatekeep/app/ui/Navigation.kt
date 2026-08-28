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
import com.gatekeep.app.ui.profiles.ProfileHubScreen
import com.gatekeep.app.ui.profiles.ProfileLimitsScreen
import com.gatekeep.app.ui.profiles.ProfilePinScreen
import com.gatekeep.app.ui.profiles.ProfileRulesScreen
import com.gatekeep.app.ui.profiles.RuleLimitActionScreen
import com.gatekeep.app.ui.profiles.RuleOpenActionScreen
import com.gatekeep.app.ui.profiles.RuleSessionActionScreen
import com.gatekeep.app.ui.schedule.ScheduleEditorScreen
import com.gatekeep.app.ui.settings.LanguageSettingsScreen
import com.gatekeep.app.ui.settings.AboutSettingsScreen
import com.gatekeep.app.ui.settings.EnforcementSettingsScreen
import com.gatekeep.app.ui.settings.NotificationSettingsScreen
import com.gatekeep.app.ui.settings.SecuritySettingsScreen
import com.gatekeep.app.ui.settings.SettingsHubScreen
import com.gatekeep.app.ui.stats.StatsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val PROFILE_DETAIL = "profile/{profileId}"
    const val PROFILE_LIMITS = "profile/{profileId}/limits"
    const val PROFILE_RULES = "profile/{profileId}/rules"
    const val PROFILE_RULES_OPEN = "profile/{profileId}/rules/open"
    const val PROFILE_RULES_LIMIT = "profile/{profileId}/rules/limit"
    const val PROFILE_RULES_SESSION = "profile/{profileId}/rules/session"
    const val PROFILE_PIN = "profile/{profileId}/pin"
    const val APP_PICKER = "apps/{profileId}"
    const val SCHEDULE = "schedule/{profileId}"
    const val STATS = "stats"
    const val PAUSE = "pause"
    const val SETTINGS = "settings"
    const val SETTINGS_SECURITY = "settings/security"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_ENFORCEMENT = "settings/enforcement"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_LANGUAGE = "settings/language"

    fun profileDetail(id: Long) = "profile/$id"
    fun profileLimits(id: Long) = "profile/$id/limits"
    fun profileRules(id: Long) = "profile/$id/rules"
    fun profileRulesOpen(id: Long) = "profile/$id/rules/open"
    fun profileRulesLimit(id: Long) = "profile/$id/rules/limit"
    fun profileRulesSession(id: Long) = "profile/$id/rules/session"
    fun profilePin(id: Long) = "profile/$id/pin"
    fun appPicker(profileId: Long) = "apps/$profileId"
    fun schedule(profileId: Long) = "schedule/$profileId"
}

@Composable
fun GatekeepNavHost(
    startDestination: String,
    onEnforcementStart: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
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
            ProfileHubScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onEditApps = { navController.navigate(Routes.appPicker(profileId)) },
                onEditSchedule = { navController.navigate(Routes.schedule(profileId)) },
                onEditLimits = { navController.navigate(Routes.profileLimits(profileId)) },
                onEditRules = { navController.navigate(Routes.profileRules(profileId)) },
                onEditPin = { navController.navigate(Routes.profilePin(profileId)) },
            )
        }
        composable(
            route = Routes.PROFILE_LIMITS,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            ProfileLimitsScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_RULES,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            ProfileRulesScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onNavigateOpenRule = { navController.navigate(Routes.profileRulesOpen(profileId)) },
                onNavigateLimitRule = { navController.navigate(Routes.profileRulesLimit(profileId)) },
                onNavigateSessionRule = { navController.navigate(Routes.profileRulesSession(profileId)) },
            )
        }
        composable(
            route = Routes.PROFILE_RULES_OPEN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleOpenActionScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_RULES_LIMIT,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleLimitActionScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_RULES_SESSION,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleSessionActionScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_PIN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            ProfilePinScreen(profileId = profileId, onBack = { navController.popBackStack() })
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
            SettingsHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateSecurity = { navController.navigate(Routes.SETTINGS_SECURITY) },
                onNavigateNotifications = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                onNavigateEnforcement = { navController.navigate(Routes.SETTINGS_ENFORCEMENT) },
                onNavigateAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
                onNavigateLanguage = { navController.navigate(Routes.SETTINGS_LANGUAGE) },
                onReplayOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }
        composable(Routes.SETTINGS_SECURITY) {
            SecuritySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_NOTIFICATIONS) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_ENFORCEMENT) {
            EnforcementSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_ABOUT) {
            AboutSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_LANGUAGE) {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
