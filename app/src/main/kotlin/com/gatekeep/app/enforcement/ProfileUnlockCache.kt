package com.gatekeep.app.enforcement

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileUnlockCache @Inject constructor() {
    private val unlockedUntilMs = mutableMapOf<Long, Long>()

    fun isUnlocked(profileId: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = unlockedUntilMs[profileId] ?: return false
        if (nowMs >= until) {
            unlockedUntilMs.remove(profileId)
            return false
        }
        return true
    }

    fun unlock(profileId: Long, durationMs: Long = 15 * 60_000L) {
        unlockedUntilMs[profileId] = System.currentTimeMillis() + durationMs
    }

    fun clear(profileId: Long) {
        unlockedUntilMs.remove(profileId)
    }
}
