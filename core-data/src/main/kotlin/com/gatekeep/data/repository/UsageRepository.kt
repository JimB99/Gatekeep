package com.gatekeep.data.repository

import com.gatekeep.data.local.dao.OverrideEventDao
import com.gatekeep.data.local.dao.PauseDao
import com.gatekeep.data.local.dao.SessionStateDao
import com.gatekeep.data.local.dao.UsageAggregateDao
import com.gatekeep.data.local.dao.UsageSessionDao
import com.gatekeep.data.local.entity.OverrideEventEntity
import com.gatekeep.data.local.entity.PauseEntity
import com.gatekeep.data.local.entity.UsageAggregateEntity
import com.gatekeep.data.local.entity.UsageSessionEntity
import com.gatekeep.data.mapper.toDomain
import com.gatekeep.data.mapper.toEntity
import com.gatekeep.domain.model.OverrideMethod
import com.gatekeep.domain.PauseManager
import com.gatekeep.domain.UsageAggregator
import com.gatekeep.domain.UsageSessionRecord
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.SessionState
import com.gatekeep.domain.model.UsagePeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UsageRepository(
    private val usageSessionDao: UsageSessionDao,
    private val usageAggregateDao: UsageAggregateDao,
    private val sessionStateDao: SessionStateDao,
    private val pauseDao: PauseDao,
    private val overrideEventDao: OverrideEventDao,
) {
    suspend fun recordSession(
        packageName: String,
        profileId: Long,
        startEpochMs: Long,
        endEpochMs: Long,
    ) {
        val duration = (endEpochMs - startEpochMs).coerceAtLeast(0)
        usageSessionDao.insert(
            UsageSessionEntity(
                packageName = packageName,
                profileId = profileId,
                startEpochMs = startEpochMs,
                endEpochMs = endEpochMs,
                durationMs = duration,
            ),
        )
    }

    suspend fun aggregateAndStore(sessions: List<UsageSessionRecord>) {
        val aggregates = UsageAggregator.aggregateSessions(sessions)
        usageAggregateDao.upsertAll(
            aggregates.map {
                UsageAggregateEntity(
                    packageName = it.packageName,
                    profileId = it.profileId,
                    period = it.period.name,
                    periodStart = it.periodStartEpochMs,
                    totalMs = it.totalMs,
                )
            },
        )
    }

    suspend fun getDailyUsage(profileId: Long, packageName: String, dayStart: Long): Long =
        usageAggregateDao.getTotal(profileId, packageName, UsagePeriod.day.name, dayStart)

    suspend fun getHourlyUsage(profileId: Long, packageName: String, hourStart: Long): Long =
        usageAggregateDao.getTotal(profileId, packageName, UsagePeriod.hour.name, hourStart)

    suspend fun getWeeklyUsage(profileId: Long, packageName: String, weekStart: Long): Long =
        usageAggregateDao.getTotal(profileId, packageName, UsagePeriod.week.name, weekStart)

    suspend fun getSessionState(profileId: Long, packageName: String): SessionState? =
        sessionStateDao.get(profileId, packageName)?.toDomain()

    suspend fun saveSessionState(state: SessionState, profileId: Long) {
        sessionStateDao.upsert(state.toEntity(profileId))
    }

    suspend fun clearSessionState(profileId: Long, packageName: String) {
        sessionStateDao.delete(profileId, packageName)
    }

    suspend fun clearSessionStatesForProfile(profileId: Long) {
        sessionStateDao.deleteForProfile(profileId)
    }

    fun observeActivePauses(now: Long): Flow<List<Pause>> =
        pauseDao.observeActive(now).map { list -> list.map { it.toDomain() } }

    suspend fun addPause(
        type: PauseType,
        nowEpochMs: Long,
        profileId: Long? = null,
        packageName: String? = null,
        untilEpochMs: Long? = null,
    ) {
        val pause = PauseManager.createPause(type, nowEpochMs, profileId, packageName, untilEpochMs)
        pauseDao.insert(
            PauseEntity(
                profileId = pause.profileId,
                packageName = pause.packageName,
                type = pause.type.name,
                untilEpochMs = pause.untilEpochMs,
            ),
        )
    }

    suspend fun logOverride(
        packageName: String,
        profileId: Long,
        method: OverrideMethod,
        extensionMs: Long,
    ) {
        overrideEventDao.insert(
            OverrideEventEntity(
                packageName = packageName,
                profileId = profileId,
                timestamp = System.currentTimeMillis(),
                method = method.storageValue,
                extensionMs = extensionMs,
            ),
        )
    }

    suspend fun getOverrideCount(profileId: Long): Int =
        overrideEventDao.countForProfile(profileId)

    suspend fun countExtensionOverridesToday(
        profileId: Long,
        packageName: String,
        dayStartMs: Long,
        sharedPool: Boolean,
    ): Int = if (sharedPool) {
        overrideEventDao.countExtensionOverridesForProfileToday(profileId, dayStartMs)
    } else {
        overrideEventDao.countExtensionOverridesForPackageToday(profileId, packageName, dayStartMs)
    }

    suspend fun countOverridesForPackageToday(
        profileId: Long,
        packageName: String,
        dayStartMs: Long,
    ): Int = overrideEventDao.countOverridesForPackageToday(profileId, packageName, dayStartMs)

    suspend fun sumExtensionMsForPackageSince(
        profileId: Long,
        packageName: String,
        sinceMs: Long,
    ): Long = overrideEventDao.sumExtensionMsForPackageSince(profileId, packageName, sinceMs)

    suspend fun sumExtensionMsForProfileSince(
        profileId: Long,
        sinceMs: Long,
    ): Long = overrideEventDao.sumExtensionMsForProfileSince(profileId, sinceMs)

    suspend fun clearExtensionOverridesForProfileSince(profileId: Long, sinceMs: Long) {
        overrideEventDao.deleteExtensionOverridesForProfileSince(profileId, sinceMs)
    }

    suspend fun getRecentOverridesForProfile(profileId: Long, limit: Int = 50) =
        overrideEventDao.getRecent(profileId, limit)

    suspend fun getRecentOverridesForPackage(
        profileId: Long,
        packageName: String,
        limit: Int = 10,
    ): List<OverrideEventEntity> =
        overrideEventDao.getRecentOverridesForPackage(profileId, packageName, limit)

    suspend fun addNoLimitTodayPause(
        profileId: Long,
        packageName: String,
        dayEndMs: Long,
        nowEpochMs: Long,
    ) {
        addPause(
            type = PauseType.noLimitToday,
            nowEpochMs = nowEpochMs,
            profileId = profileId,
            packageName = packageName,
            untilEpochMs = dayEndMs,
        )
    }

    suspend fun addExtensionGracePause(
        profileId: Long,
        packageName: String?,
        untilEpochMs: Long,
        nowEpochMs: Long,
    ) {
        addPause(
            type = PauseType.extensionGrace,
            nowEpochMs = nowEpochMs,
            profileId = profileId,
            packageName = packageName,
            untilEpochMs = untilEpochMs,
        )
    }

    suspend fun addFocusBlock(
        profileId: Long?,
        untilEpochMs: Long,
        nowEpochMs: Long,
    ) {
        addPause(
            type = PauseType.focusBlock,
            nowEpochMs = nowEpochMs,
            profileId = profileId,
            packageName = null,
            untilEpochMs = untilEpochMs,
        )
    }

    suspend fun clearFocusBlocks(profileIds: List<Long>?) {
        if (profileIds == null) {
            pauseDao.deleteGlobalFocusBlocks()
        } else {
            profileIds.forEach { pauseDao.deleteFocusBlocksForProfile(it) }
        }
    }

    suspend fun clearAllowPauses(profileIds: List<Long>?) {
        if (profileIds == null) {
            pauseDao.deleteGlobalAllowPauses()
        } else {
            profileIds.forEach { pauseDao.deleteAllowPausesForProfile(it) }
        }
    }

    suspend fun getRecentSessions(profileId: Long, limit: Int = 100) =
        usageSessionDao.getRecent(profileId, limit)
}
