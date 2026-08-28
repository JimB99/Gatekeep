package com.gatekeep.domain

object TrackedAppMerge {

    fun mergeDailyLimit(existing: Long?, incoming: Long?): Long? = when {
        existing == null -> incoming
        incoming == null -> existing
        else -> minOf(existing, incoming)
    }
}
