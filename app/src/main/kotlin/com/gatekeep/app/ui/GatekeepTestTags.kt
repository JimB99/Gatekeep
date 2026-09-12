package com.gatekeep.app.ui

object GatekeepTestTags {
    const val ONBOARDING_ROOT = "onboarding_root"
    const val ONBOARDING_GET_STARTED = "onboarding_get_started"
    const val ONBOARDING_SKIP = "onboarding_skip"
    const val DASHBOARD_ROOT = "dashboard_root"
    const val NAV_STATS = "nav_stats"
    const val NAV_PAUSE = "nav_pause"
    const val NAV_SETTINGS = "nav_settings"
    const val PROFILE_CARD_PREFIX = "profile_card_"
    const val PERMISSION_BANNER = "permission_banner"
    const val PERMISSION_ACCESSIBILITY_BUTTON = "permission_accessibility_button"
    const val SETTINGS_LANGUAGE = "settings_language"
    const val SETTINGS_ENFORCEMENT_TOGGLE = "settings_enforcement_toggle"
    const val SETTINGS_SESSION_TIMER_TOGGLE = "settings_session_timer_toggle"
    const val SETTINGS_WEEKLY_REPORT_TOGGLE = "settings_weekly_report_toggle"
    const val LANGUAGE_OPTION_PREFIX = "language_option_"
    const val CURRENT_USAGE_EXTEND_PREFIX = "current_usage_extend_"
    const val CURRENT_USAGE_RESET = "current_usage_reset"
    const val CURRENT_USAGE_RESET_PREFIX = "current_usage_reset_"
    const val CURRENT_USAGE_NO_LIMIT = "current_usage_no_limit_today"
    const val PAUSE_ALLOW_FIVE_MIN = "pause_allow_five_min"
    const val PAUSE_ALLOW_FIFTEEN_MIN = "pause_allow_fifteen_min"
    const val PAUSE_FOCUS_FIVE_MIN = "pause_focus_five_min"
    const val PAUSE_RESET_ACTION = "pause_reset_action"
    const val PAUSE_END_EARLY = "pause_end_early"

    fun profileCard(name: String): String = PROFILE_CARD_PREFIX + name
    const val PROFILE_PIN_FIELD = "profile_pin_field"
    const val PROFILE_PIN_SAVE = "profile_pin_save"
    const val STATS_ROOT = "stats_root"
}
