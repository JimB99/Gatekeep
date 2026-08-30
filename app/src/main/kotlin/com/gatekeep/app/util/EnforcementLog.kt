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
        dataStore.edit()
            .remove(KEY_LAST_ERROR)
            .remove(KEY_LAST_ERROR_TIME)
            .apply()
    }

    fun clearStaleMigrationErrors() {
        val error = getLastError() ?: return
        if (STALE_MIGRATION_MARKERS.any { marker -> error.contains(marker, ignoreCase = true) }) {
            clear()
        }
    }

    companion object {
        private const val PREFS = "gatekeep_enforcement_log"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_ERROR_TIME = "last_error_time"

        private val STALE_MIGRATION_MARKERS = listOf(
            "schedule_segments",
            "Migration didn't properly handle",
            "Migration",
        )
    }
}
