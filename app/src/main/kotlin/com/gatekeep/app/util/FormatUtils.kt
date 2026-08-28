package com.gatekeep.app.util

import android.content.Context
import com.gatekeep.app.R
import java.security.MessageDigest

object PasswordHasher {
    fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(password: String, hash: String): Boolean = hash(password) == hash
}

fun formatDurationMs(ms: Long?): String = formatDurationMs(null, ms, includeSeconds = true)

fun formatDurationMinutes(ms: Long?): String = formatDurationMs(null, ms, includeSeconds = false)

fun formatDurationMs(context: Context?, ms: Long?): String =
    formatDurationMs(context, ms, includeSeconds = true)

private fun formatDurationMs(context: Context?, ms: Long?, includeSeconds: Boolean): String {
    if (ms == null) {
        return context?.getString(R.string.duration_infinity) ?: "∞"
    }
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> context?.getString(R.string.duration_hours_minutes, hours, minutes)
            ?: "${hours}h ${minutes}m"
        minutes > 0 -> if (includeSeconds) {
            context?.getString(R.string.duration_minutes_seconds, minutes, seconds)
                ?: "${minutes}m ${seconds}s"
        } else {
            context?.getString(R.string.duration_hours_minutes, 0, minutes)
                ?: "${minutes}m"
        }
        else -> if (includeSeconds) {
            context?.getString(R.string.duration_seconds, seconds) ?: "${seconds}s"
        } else {
            context?.getString(R.string.duration_zero_minutes) ?: "0m"
        }
    }
}

fun minutesToMs(minutes: Int): Long = minutes * 60_000L

fun formatChartAxisTick(ms: Long): String {
    val totalMinutes = (ms / 60_000).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
