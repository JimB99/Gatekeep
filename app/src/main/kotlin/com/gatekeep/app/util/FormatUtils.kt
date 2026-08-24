package com.gatekeep.app.util

import java.security.MessageDigest

object PasswordHasher {
    fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(password: String, hash: String): Boolean = hash(password) == hash
}

fun formatDurationMs(ms: Long?): String = formatDurationMs(ms, includeSeconds = true)

fun formatDurationMinutes(ms: Long?): String = formatDurationMs(ms, includeSeconds = false)

private fun formatDurationMs(ms: Long?, includeSeconds: Boolean): String {
    if (ms == null) return "∞"
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> if (includeSeconds) "${minutes}m ${seconds}s" else "${minutes}m"
        else -> if (includeSeconds) "${seconds}s" else "0m"
    }
}

fun minutesToMs(minutes: Int): Long = minutes * 60_000L
