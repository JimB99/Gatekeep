package com.gatekeep.domain

object SessionContinuityGrace {
    const val DEFAULT_MS = 60_000L
    const val MIN_MS = 15_000L
    const val MAX_MS = 15 * 60_000L

    fun coerce(durationMs: Long): Long = durationMs.coerceIn(MIN_MS, MAX_MS)
}
