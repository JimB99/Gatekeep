package com.gatekeep.app.util

import android.content.Context

class EnforcementLog(context: Context) {

    private val dataStore = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun logError(message: String, throwable: Throwable? = null) {
        val full = buildString {
            append(System.currentTimeMillis())
            append(": ")
            append(message)
            throwable?.let { append(" — ").append(it.message) }
        }
        dataStore.edit()
            .putString(KEY_LAST_ERROR, full)
            .putLong(KEY_LAST_ERROR_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getLastError(): String? = dataStore.getString(KEY_LAST_ERROR, null)

    fun getLastErrorTime(): Long = dataStore.getLong(KEY_LAST_ERROR_TIME, 0L)

    fun clear() {
        dataStore.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "gatekeep_enforcement_log"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_ERROR_TIME = "last_error_time"
    }
}
