package com.gatekeep.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gatekeep.data.locale.LocalePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gatekeep_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val appLockEnabled: Boolean = false,
    val appPasswordHash: String? = null,
    val hudEnabled: Boolean = true,
    val hudOpacity: Float = 0.9f,
    val strictMode: Boolean = false,
    val deviceAdminEnabled: Boolean = false,
    val focusModeUntilMs: Long? = null,
    val lastEmergencyBypassEpochMs: Long? = null,
    val lastUsageSyncEpochMs: Long = 0,
    val enforcementEnabled: Boolean = true,
    val themeMode: String = "system",
    val showSessionTimerNotification: Boolean = true,
    val warningAlertsEnabled: Boolean = true,
    val weeklyReportEnabled: Boolean = true,
    val weeklyReportDayOfWeek: Int = 0,
    val weeklyReportMinuteOfDay: Int = 10 * 60,
    val languageTag: String = "en-GB",
) {
    fun hasAppPin(): Boolean = !appPasswordHash.isNullOrBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val APP_PASSWORD = stringPreferencesKey("app_password_hash")
        val HUD_ENABLED = booleanPreferencesKey("hud_enabled")
        val HUD_OPACITY = floatPreferencesKey("hud_opacity")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val DEVICE_ADMIN = booleanPreferencesKey("device_admin_enabled")
        val FOCUS_UNTIL = longPreferencesKey("focus_mode_until_ms")
        val EMERGENCY_BYPASS = longPreferencesKey("last_emergency_bypass_ms")
        val LAST_SYNC = longPreferencesKey("last_usage_sync_ms")
        val ENFORCEMENT = booleanPreferencesKey("enforcement_enabled")
        val THEME = stringPreferencesKey("theme_mode")
        val SESSION_TIMER_NOTIF = booleanPreferencesKey("session_timer_notification")
        val WARNING_ALERTS = booleanPreferencesKey("warning_alerts_enabled")
        val WEEKLY_REPORT = booleanPreferencesKey("weekly_report_enabled")
        val WEEKLY_REPORT_DAY = intPreferencesKey("weekly_report_day")
        val WEEKLY_REPORT_MINUTE = intPreferencesKey("weekly_report_minute")
        val LANGUAGE = stringPreferencesKey("language_tag")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs -> readSettings(prefs) }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING] = complete }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            writeSettings(prefs, transform(readSettings(prefs)))
        }
    }

    private fun readSettings(prefs: Preferences) = AppSettings(
        onboardingComplete = prefs[Keys.ONBOARDING] ?: false,
        appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
        appPasswordHash = prefs[Keys.APP_PASSWORD]?.takeIf { it.isNotBlank() },
        hudEnabled = prefs[Keys.HUD_ENABLED] ?: true,
        hudOpacity = prefs[Keys.HUD_OPACITY] ?: 0.9f,
        strictMode = prefs[Keys.STRICT_MODE] ?: false,
        deviceAdminEnabled = prefs[Keys.DEVICE_ADMIN] ?: false,
        focusModeUntilMs = prefs[Keys.FOCUS_UNTIL],
        lastEmergencyBypassEpochMs = prefs[Keys.EMERGENCY_BYPASS],
        lastUsageSyncEpochMs = prefs[Keys.LAST_SYNC] ?: 0,
        enforcementEnabled = prefs[Keys.ENFORCEMENT] ?: true,
        themeMode = prefs[Keys.THEME] ?: "system",
        showSessionTimerNotification = prefs[Keys.SESSION_TIMER_NOTIF]
            ?: prefs[Keys.HUD_ENABLED]
            ?: true,
        warningAlertsEnabled = prefs[Keys.WARNING_ALERTS] ?: true,
        weeklyReportEnabled = prefs[Keys.WEEKLY_REPORT] ?: true,
        weeklyReportDayOfWeek = prefs[Keys.WEEKLY_REPORT_DAY] ?: 0,
        weeklyReportMinuteOfDay = prefs[Keys.WEEKLY_REPORT_MINUTE] ?: (10 * 60),
        languageTag = prefs[Keys.LANGUAGE]?.takeIf { it in LocalePreferences.SUPPORTED_TAGS }
            ?: LocalePreferences.read(context),
    )

    private fun writeSettings(prefs: MutablePreferences, updated: AppSettings) {
        prefs[Keys.ONBOARDING] = updated.onboardingComplete
        prefs[Keys.APP_LOCK] = updated.appLockEnabled
        if (updated.appPasswordHash.isNullOrBlank()) {
            prefs.remove(Keys.APP_PASSWORD)
        } else {
            prefs[Keys.APP_PASSWORD] = updated.appPasswordHash
        }
        prefs[Keys.HUD_ENABLED] = updated.hudEnabled
        prefs[Keys.HUD_OPACITY] = updated.hudOpacity
        prefs[Keys.STRICT_MODE] = updated.strictMode
        prefs[Keys.DEVICE_ADMIN] = updated.deviceAdminEnabled
        updated.focusModeUntilMs?.let { prefs[Keys.FOCUS_UNTIL] = it }
        updated.lastEmergencyBypassEpochMs?.let { prefs[Keys.EMERGENCY_BYPASS] = it }
        prefs[Keys.LAST_SYNC] = updated.lastUsageSyncEpochMs
        prefs[Keys.ENFORCEMENT] = updated.enforcementEnabled
        prefs[Keys.THEME] = updated.themeMode
        prefs[Keys.SESSION_TIMER_NOTIF] = updated.showSessionTimerNotification
        prefs[Keys.WARNING_ALERTS] = updated.warningAlertsEnabled
        prefs[Keys.WEEKLY_REPORT] = updated.weeklyReportEnabled
        prefs[Keys.WEEKLY_REPORT_DAY] = updated.weeklyReportDayOfWeek.coerceIn(0, 6)
        prefs[Keys.WEEKLY_REPORT_MINUTE] = updated.weeklyReportMinuteOfDay.coerceIn(0, 24 * 60 - 1)
        val languageTag = LocalePreferences.normalizeTag(updated.languageTag)
        prefs[Keys.LANGUAGE] = languageTag
        LocalePreferences.write(context, languageTag)
    }
}
