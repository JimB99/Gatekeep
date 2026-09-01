package com.gatekeep.app.enforcement

import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.SessionTracker
import com.gatekeep.domain.model.SessionState

class SessionLifecycleService(
    private val usageRepository: UsageRepository,
) {
    suspend fun getOrStart(profileId: Long, packageName: String, nowEpochMs: Long): SessionState =
        usageRepository.getSessionState(profileId, packageName)
            ?: SessionTracker.startSession(packageName, nowEpochMs)

    suspend fun save(profileId: Long, state: SessionState) {
        usageRepository.saveSessionState(state, profileId)
    }

    suspend fun clear(profileId: Long, packageName: String) {
        usageRepository.clearSessionState(profileId, packageName)
    }
}
