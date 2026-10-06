package com.gatekeep.app.enforcement

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Survives process death so pocket lock still freezes the session clock (RES-04).
 * In-memory [ScreenPauseTracker] resets to screen-on after a kill.
 */
@Singleton
class DevicePauseStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pausedAtEpochMs(): Long? {
        val value = prefs.getLong(KEY_PAUSED_AT, ABSENT)
        return value.takeIf { it != ABSENT }
    }

    fun setPausedAt(epochMs: Long) {
        if (pausedAtEpochMs() != null) return
        prefs.edit().putLong(KEY_PAUSED_AT, epochMs).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PAUSED_AT).apply()
    }

    private companion object {
        const val PREFS = "device_use_pause"
        const val KEY_PAUSED_AT = "pausedAtEpochMs"
        const val ABSENT = -1L
    }
}
