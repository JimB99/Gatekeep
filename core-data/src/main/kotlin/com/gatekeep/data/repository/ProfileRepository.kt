package com.gatekeep.data.repository

import androidx.room.Transaction
import com.gatekeep.data.local.dao.AppLimitDao
import com.gatekeep.data.local.dao.MonitoredAppDao
import com.gatekeep.data.local.dao.OverrideEventDao
import com.gatekeep.data.local.dao.PauseDao
import com.gatekeep.data.local.dao.ProfileDao
import com.gatekeep.data.local.dao.ScheduleSegmentDao
import com.gatekeep.data.local.dao.ScheduleWindowDao
import com.gatekeep.data.local.dao.SessionStateDao
import com.gatekeep.data.local.dao.UsageAggregateDao
import com.gatekeep.data.local.dao.UsageSessionDao
import com.gatekeep.data.mapper.toDomain
import com.gatekeep.data.mapper.toEntity
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.MonitoredApp
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val monitoredAppDao: MonitoredAppDao,
    private val appLimitDao: AppLimitDao,
    private val scheduleSegmentDao: ScheduleSegmentDao,
    private val scheduleWindowDao: ScheduleWindowDao,
    private val pauseDao: PauseDao,
    private val usageSessionDao: UsageSessionDao,
    private val usageAggregateDao: UsageAggregateDao,
    private val overrideEventDao: OverrideEventDao,
    private val sessionStateDao: SessionStateDao,
) {
    fun observeProfiles(): Flow<List<Profile>> =
        profileDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveProfiles(): Flow<List<Profile>> =
        profileDao.observeActiveProfiles().map { list -> list.map { it.toDomain() } }

    fun observeActiveProfile(): Flow<Profile?> =
        profileDao.observeActive().map { it?.toDomain() }

    suspend fun createProfile(name: String): Long {
        val sortOrder = (profileDao.maxSortOrder() ?: -1) + 1
        return profileDao.insert(
            com.gatekeep.data.local.entity.ProfileEntity(name = name, isActive = false, sortOrder = sortOrder),
        )
    }

    @Transaction
    suspend fun reorderProfiles(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            profileDao.setSortOrder(id, index)
        }
    }

    @Transaction
    suspend fun duplicateProfile(id: Long, copyName: String): Long? {
        val source = profileDao.getById(id) ?: return null
        val ordered = profileDao.getAll()
        val insertAt = ordered.indexOfFirst { it.id == id }.let { index ->
            if (index < 0) ordered.size else index + 1
        }
        val newId = profileDao.insert(
            source.copy(id = 0, name = copyName, isActive = false, sortOrder = insertAt),
        )

        monitoredAppDao.getForProfile(id).forEach { app ->
            monitoredAppDao.upsert(app.copy(profileId = newId))
        }
        appLimitDao.getForProfile(id).forEach { limit ->
            appLimitDao.upsert(limit.copy(profileId = newId))
        }

        val segmentIdMap = mutableMapOf<Long, Long>()
        scheduleSegmentDao.getForProfile(id).forEach { segment ->
            val newSegmentId = scheduleSegmentDao.insert(segment.copy(id = 0, profileId = newId))
            segmentIdMap[segment.id] = newSegmentId
        }
        scheduleWindowDao.getForProfile(id).forEach { window ->
            scheduleWindowDao.insert(
                window.copy(
                    id = 0,
                    profileId = newId,
                    segmentId = window.segmentId?.let { segmentIdMap[it] },
                ),
            )
        }

        val ids = ordered.map { it.id }.toMutableList()
        ids.add(insertAt, newId)
        reorderProfiles(ids)
        return newId
    }

    suspend fun toggleProfileActive(id: Long, active: Boolean) {
        profileDao.setProfileActive(id, active)
        pauseDao.deleteNoLimitTodayForProfile(id)
    }

    suspend fun activateProfile(id: Long) {
        profileDao.setProfileActive(id, true)
    }

    @Transaction
    suspend fun deleteProfile(id: Long) {
        scheduleWindowDao.deleteForProfile(id)
        scheduleSegmentDao.deleteForProfile(id)
        monitoredAppDao.deleteForProfile(id)
        appLimitDao.deleteForProfile(id)
        pauseDao.deleteForProfile(id)
        usageSessionDao.deleteForProfile(id)
        usageAggregateDao.deleteForProfile(id)
        overrideEventDao.deleteForProfile(id)
        sessionStateDao.deleteForProfile(id)
        profileDao.delete(id)
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.insert(profile.toEntity())
    }

    fun observeMonitoredApps(profileId: Long): Flow<List<MonitoredApp>> =
        monitoredAppDao.observeForProfile(profileId).map { list -> list.map { it.toDomain() } }

    suspend fun addMonitoredApp(app: MonitoredApp) {
        monitoredAppDao.upsert(
            com.gatekeep.data.local.entity.MonitoredAppEntity(
                profileId = app.profileId,
                packageName = app.packageName,
                label = app.label,
                category = app.category.name,
                isWhitelistedEssential = app.isWhitelistedEssential,
            ),
        )
    }

    suspend fun removeMonitoredApp(profileId: Long, packageName: String) {
        monitoredAppDao.delete(profileId, packageName)
    }

    suspend fun updateMonitoredApp(app: MonitoredApp) {
        addMonitoredApp(app)
    }

    fun observeLimits(profileId: Long): Flow<List<AppLimit>> =
        appLimitDao.observeForProfile(profileId).map { list -> list.map { it.toDomain() } }

    suspend fun upsertLimit(limit: AppLimit) {
        appLimitDao.upsert(limit.toEntity())
    }

    suspend fun getLimit(profileId: Long, packageName: String): AppLimit? =
        appLimitDao.get(profileId, packageName)?.toDomain()

    fun observeScheduleWindows(profileId: Long): Flow<List<ScheduleWindow>> =
        scheduleWindowDao.observeForProfile(profileId).map { list -> list.map { it.toDomain() } }

    fun observeAllScheduleWindows(): Flow<List<ScheduleWindow>> =
        scheduleWindowDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeScheduleSegments(profileId: Long): Flow<List<ScheduleSegment>> =
        scheduleSegmentDao.observeForProfile(profileId).map { list -> list.map { it.toDomain() } }

    fun observeAllScheduleSegments(): Flow<List<ScheduleSegment>> =
        scheduleSegmentDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun upsertScheduleSegment(segment: ScheduleSegment): Long {
        return scheduleSegmentDao.insert(segment.toEntity())
    }

    suspend fun updateScheduleSegment(segment: ScheduleSegment) {
        scheduleSegmentDao.update(segment.toEntity())
    }

    suspend fun getScheduleSegment(segmentId: Long): ScheduleSegment? =
        scheduleSegmentDao.getById(segmentId)?.toDomain()

    suspend fun deleteScheduleSegment(segmentId: Long) {
        scheduleWindowDao.deleteForSegment(segmentId)
        scheduleSegmentDao.delete(segmentId)
    }

    suspend fun toggleScheduleSegmentActive(segmentId: Long, active: Boolean) {
        val segment = scheduleSegmentDao.getById(segmentId) ?: return
        scheduleSegmentDao.update(segment.copy(isActive = active))
    }

    suspend fun duplicateScheduleSegment(segmentId: Long, copyLabel: String? = null): Long? {
        val segment = scheduleSegmentDao.getById(segmentId)?.toDomain() ?: return null
        val windows = scheduleWindowDao.observeForProfile(segment.profileId).first()
            .filter { it.segmentId == segmentId }
            .map { it.toDomain() }

        val newLabel = copyLabel ?: segment.label?.let { "$it (copy)" }
        val newSegmentId = scheduleSegmentDao.insert(
            segment.copy(
                id = 0,
                label = newLabel,
                sortOrder = segment.sortOrder + 1,
            ).toEntity(),
        )

        windows.forEach { window ->
            scheduleWindowDao.insert(
                window.copy(id = 0, segmentId = newSegmentId).toEntity(),
            )
        }
        return newSegmentId
    }

    suspend fun addScheduleWindow(window: ScheduleWindow) {
        scheduleWindowDao.insert(window.toEntity())
    }

    suspend fun deleteScheduleWindow(id: Long) {
        scheduleWindowDao.delete(id)
    }
}
