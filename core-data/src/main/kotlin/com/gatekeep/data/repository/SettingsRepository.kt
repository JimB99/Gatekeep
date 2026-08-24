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
    val quietHoursStartMinute: Int? = null,
    val quietHoursEndMinute: Int? = null,
)

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
        val QUIET_START = intPreferencesKey("quiet_hours_start")
        val QUIET_END = intPreferencesKey("quiet_hours_end")
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

    fun isQuietHours(nowMinuteOfDay: Int, settings: AppSettings): Boolean {
        val start = settings.quietHoursStartMinute ?: return false
        val end = settings.quietHoursEndMinute ?: return false
        return if (start <= end) {
            nowMinuteOfDay in start until end
        } else {
            nowMinuteOfDay >= start || nowMinuteOfDay < end
        }
    }

    private fun readSettings(prefs: Preferences) = AppSettings(
        onboardingComplete = prefs[Keys.ONBOARDING] ?: false,
        appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
        appPasswordHash = prefs[Keys.APP_PASSWORD],
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
        quietHoursStartMinute = prefs[Keys.QUIET_START],
        quietHoursEndMinute = prefs[Keys.QUIET_END],
    )

    private fun writeSettings(prefs: MutablePreferences, updated: AppSettings) {
        prefs[Keys.ONBOARDING] = updated.onboardingComplete
        prefs[Keys.APP_LOCK] = updated.appLockEnabled
        updated.appPasswordHash?.let { prefs[Keys.APP_PASSWORD] = it }
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
        updated.quietHoursStartMinute?.let { prefs[Keys.QUIET_START] = it }
        updated.quietHoursEndMinute?.let { prefs[Keys.QUIET_END] = it }
    }
}
