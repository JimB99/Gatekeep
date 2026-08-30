package com.gatekeep.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gatekeep.app.ui.apps.AppPickerScreen
import com.gatekeep.app.ui.home.ProfilesHomeScreen
import com.gatekeep.app.ui.onboarding.OnboardingScreen
import com.gatekeep.app.ui.pause.PauseScreen
import com.gatekeep.app.ui.policy.PolicyOverrideLimitsScreen
import com.gatekeep.app.ui.policy.PolicyOverrideRulesHubScreen
import com.gatekeep.app.ui.policy.PolicyOverrideScope
import com.gatekeep.app.ui.policy.ProfilePolicyScreen
import com.gatekeep.app.ui.policy.SegmentEditorScreen
import com.gatekeep.app.ui.profiles.ProfileHubScreen
import com.gatekeep.app.ui.profiles.ProfileLimitsScreen
import com.gatekeep.app.ui.profiles.ProfilePinScreen
import com.gatekeep.app.ui.profiles.RuleLimitActionScreen
import com.gatekeep.app.ui.profiles.RuleOpenActionScreen
import com.gatekeep.app.ui.profiles.RuleSessionActionScreen
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
    const val PROFILE_POLICY = "profile/{profileId}/policy"
    const val PROFILE_POLICY_LIMITS = "profile/{profileId}/policy/limits"
    const val PROFILE_POLICY_RULES_OPEN = "profile/{profileId}/policy/rules/open"
    const val PROFILE_POLICY_RULES_LIMIT = "profile/{profileId}/policy/rules/limit"
    const val PROFILE_POLICY_RULES_SESSION = "profile/{profileId}/policy/rules/session"
    const val PROFILE_POLICY_SEGMENT = "profile/{profileId}/policy/segment/{segmentId}"
    const val PROFILE_POLICY_SEGMENT_NEW = "profile/{profileId}/policy/segment/new"
    const val PROFILE_POLICY_NO_MATCH_LIMITS = "profile/{profileId}/policy/no-match/limits"
    const val PROFILE_POLICY_NO_MATCH_RULES = "profile/{profileId}/policy/no-match/rules"
    const val PROFILE_POLICY_NO_MATCH_RULES_OPEN = "profile/{profileId}/policy/no-match/rules/open"
    const val PROFILE_POLICY_NO_MATCH_RULES_LIMIT = "profile/{profileId}/policy/no-match/rules/limit"
    const val PROFILE_POLICY_NO_MATCH_RULES_SESSION = "profile/{profileId}/policy/no-match/rules/session"
    const val PROFILE_POLICY_SEGMENT_CUSTOMIZE_LIMITS = "profile/{profileId}/policy/segment/{segmentId}/customize/limits"
    const val PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES = "profile/{profileId}/policy/segment/{segmentId}/customize/rules"
    const val PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES_OPEN = "profile/{profileId}/policy/segment/{segmentId}/customize/rules/open"
    const val PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES_LIMIT = "profile/{profileId}/policy/segment/{segmentId}/customize/rules/limit"
    const val PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES_SESSION = "profile/{profileId}/policy/segment/{segmentId}/customize/rules/session"
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
    fun profilePolicy(id: Long) = "profile/$id/policy"
    fun profilePolicyLimits(id: Long) = "profile/$id/policy/limits"
    fun profilePolicyRulesOpen(id: Long) = "profile/$id/policy/rules/open"
    fun profilePolicyRulesLimit(id: Long) = "profile/$id/policy/rules/limit"
    fun profilePolicyRulesSession(id: Long) = "profile/$id/policy/rules/session"
    fun profilePolicySegment(id: Long, segmentId: Long) = "profile/$id/policy/segment/$segmentId"
    fun profilePolicySegmentNew(id: Long) = "profile/$id/policy/segment/new"
    fun profilePolicyNoMatchLimits(id: Long) = "profile/$id/policy/no-match/limits"
    fun profilePolicyNoMatchRules(id: Long) = "profile/$id/policy/no-match/rules"
    fun profilePolicyNoMatchRulesOpen(id: Long) = "profile/$id/policy/no-match/rules/open"
    fun profilePolicyNoMatchRulesLimit(id: Long) = "profile/$id/policy/no-match/rules/limit"
    fun profilePolicyNoMatchRulesSession(id: Long) = "profile/$id/policy/no-match/rules/session"
    fun profilePolicySegmentCustomizeLimits(id: Long, segmentId: Long) =
        "profile/$id/policy/segment/$segmentId/customize/limits"
    fun profilePolicySegmentCustomizeRules(id: Long, segmentId: Long) =
        "profile/$id/policy/segment/$segmentId/customize/rules"
    fun profilePolicySegmentCustomizeRulesOpen(id: Long, segmentId: Long) =
        "profile/$id/policy/segment/$segmentId/customize/rules/open"
    fun profilePolicySegmentCustomizeRulesLimit(id: Long, segmentId: Long) =
        "profile/$id/policy/segment/$segmentId/customize/rules/limit"
    fun profilePolicySegmentCustomizeRulesSession(id: Long, segmentId: Long) =
        "profile/$id/policy/segment/$segmentId/customize/rules/session"
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
                onNavigatePolicy = { profileId -> navController.navigate(Routes.profilePolicy(profileId)) },
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
                onEditPolicy = { navController.navigate(Routes.profilePolicy(profileId)) },
                onEditPin = { navController.navigate(Routes.profilePin(profileId)) },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            ProfilePolicyScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onNavigateLimits = { navController.navigate(Routes.profilePolicyLimits(profileId)) },
                onNavigateRulesOpen = { navController.navigate(Routes.profilePolicyRulesOpen(profileId)) },
                onNavigateRulesLimit = { navController.navigate(Routes.profilePolicyRulesLimit(profileId)) },
                onNavigateRulesSession = { navController.navigate(Routes.profilePolicyRulesSession(profileId)) },
                onNavigateNoMatchLimits = { navController.navigate(Routes.profilePolicyNoMatchLimits(profileId)) },
                onNavigateNoMatchRules = { navController.navigate(Routes.profilePolicyNoMatchRules(profileId)) },
                onNavigateSegmentEditor = { segmentId ->
                    if (segmentId == null) {
                        navController.navigate(Routes.profilePolicySegmentNew(profileId))
                    } else {
                        navController.navigate(Routes.profilePolicySegment(profileId, segmentId))
                    }
                },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_LIMITS,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            ProfileLimitsScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_POLICY_RULES_OPEN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleOpenActionScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_POLICY_RULES_LIMIT,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleLimitActionScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_POLICY_RULES_SESSION,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleSessionActionScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE_POLICY_SEGMENT,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType },
                navArgument("segmentId") { type = NavType.LongType },
            ),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            val segmentId = entry.arguments?.getLong("segmentId") ?: return@composable
            SegmentEditorScreen(
                profileId = profileId,
                segmentId = segmentId,
                onBack = { navController.popBackStack() },
                onNavigateCustomizeLimits = { segId ->
                    navController.navigate(Routes.profilePolicySegmentCustomizeLimits(profileId, segId))
                },
                onNavigateCustomizeRules = { segId ->
                    navController.navigate(Routes.profilePolicySegmentCustomizeRules(profileId, segId))
                },
                onDelete = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_SEGMENT_NEW,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            SegmentEditorScreen(
                profileId = profileId,
                segmentId = null,
                onBack = { navController.popBackStack() },
                onNavigateCustomizeLimits = { segId ->
                    navController.navigate(Routes.profilePolicySegmentCustomizeLimits(profileId, segId))
                },
                onNavigateCustomizeRules = { segId ->
                    navController.navigate(Routes.profilePolicySegmentCustomizeRules(profileId, segId))
                },
                onSegmentCreated = { newId ->
                    navController.navigate(Routes.profilePolicySegment(profileId, newId)) {
                        popUpTo(Routes.profilePolicySegmentNew(profileId)) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_NO_MATCH_LIMITS,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            PolicyOverrideLimitsScreen(
                profileId = profileId,
                scope = PolicyOverrideScope.NoScheduleMatch(profileId),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_NO_MATCH_RULES,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            PolicyOverrideRulesHubScreen(
                profileId = profileId,
                scope = PolicyOverrideScope.NoScheduleMatch(profileId),
                onBack = { navController.popBackStack() },
                onNavigateOpen = { navController.navigate(Routes.profilePolicyNoMatchRulesOpen(profileId)) },
                onNavigateLimit = { navController.navigate(Routes.profilePolicyNoMatchRulesLimit(profileId)) },
                onNavigateSession = { navController.navigate(Routes.profilePolicyNoMatchRulesSession(profileId)) },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_NO_MATCH_RULES_OPEN,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleOpenActionScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                overrideScope = PolicyOverrideScope.NoScheduleMatch(profileId),
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_NO_MATCH_RULES_LIMIT,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleLimitActionScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                overrideScope = PolicyOverrideScope.NoScheduleMatch(profileId),
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_NO_MATCH_RULES_SESSION,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            RuleSessionActionScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                overrideScope = PolicyOverrideScope.NoScheduleMatch(profileId),
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_SEGMENT_CUSTOMIZE_LIMITS,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType },
                navArgument("segmentId") { type = NavType.LongType },
            ),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            val segmentId = entry.arguments?.getLong("segmentId") ?: return@composable
            PolicyOverrideLimitsScreen(
                profileId = profileId,
                scope = PolicyOverrideScope.Segment(profileId, segmentId),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType },
                navArgument("segmentId") { type = NavType.LongType },
            ),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            val segmentId = entry.arguments?.getLong("segmentId") ?: return@composable
            PolicyOverrideRulesHubScreen(
                profileId = profileId,
                scope = PolicyOverrideScope.Segment(profileId, segmentId),
                onBack = { navController.popBackStack() },
                onNavigateOpen = {
                    navController.navigate(Routes.profilePolicySegmentCustomizeRulesOpen(profileId, segmentId))
                },
                onNavigateLimit = {
                    navController.navigate(Routes.profilePolicySegmentCustomizeRulesLimit(profileId, segmentId))
                },
                onNavigateSession = {
                    navController.navigate(Routes.profilePolicySegmentCustomizeRulesSession(profileId, segmentId))
                },
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES_OPEN,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType },
                navArgument("segmentId") { type = NavType.LongType },
            ),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            val segmentId = entry.arguments?.getLong("segmentId") ?: return@composable
            RuleOpenActionScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                overrideScope = PolicyOverrideScope.Segment(profileId, segmentId),
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES_LIMIT,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType },
                navArgument("segmentId") { type = NavType.LongType },
            ),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            val segmentId = entry.arguments?.getLong("segmentId") ?: return@composable
            RuleLimitActionScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                overrideScope = PolicyOverrideScope.Segment(profileId, segmentId),
            )
        }
        composable(
            route = Routes.PROFILE_POLICY_SEGMENT_CUSTOMIZE_RULES_SESSION,
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType },
                navArgument("segmentId") { type = NavType.LongType },
            ),
        ) { entry ->
            val profileId = entry.arguments?.getLong("profileId") ?: return@composable
            val segmentId = entry.arguments?.getLong("segmentId") ?: return@composable
            RuleSessionActionScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                overrideScope = PolicyOverrideScope.Segment(profileId, segmentId),
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
            LaunchedEffect(profileId) {
                navController.navigate(Routes.profilePolicy(profileId)) {
                    popUpTo(Routes.profileRules(profileId)) { inclusive = true }
                }
            }
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
            LaunchedEffect(profileId) {
                navController.navigate(Routes.profilePolicy(profileId)) {
                    popUpTo(Routes.schedule(profileId)) { inclusive = true }
                }
            }
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
