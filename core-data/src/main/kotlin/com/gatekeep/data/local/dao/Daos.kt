package com.gatekeep.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.gatekeep.data.local.entity.AppLimitEntity
import com.gatekeep.data.local.entity.MonitoredAppEntity
import com.gatekeep.data.local.entity.OverrideEventEntity
import com.gatekeep.data.local.entity.PauseEntity
import com.gatekeep.data.local.entity.ProfileEntity
import com.gatekeep.data.local.entity.ScheduleWindowEntity
import com.gatekeep.data.local.entity.SessionStateEntity
import com.gatekeep.data.local.entity.UsageAggregateEntity
import com.gatekeep.data.local.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY sortOrder, id")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT MAX(sortOrder) FROM profiles")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT * FROM profiles WHERE isActive = 1")
    fun observeActiveProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET isActive = :active WHERE id = :id")
    suspend fun setProfileActive(id: Long, active: Boolean)

    @Query("UPDATE profiles SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps WHERE profileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: Long): List<MonitoredAppEntity>

    @Upsert
    suspend fun upsert(app: MonitoredAppEntity)

    @Upsert
    suspend fun upsertAll(apps: List<MonitoredAppEntity>)

    @Query("DELETE FROM monitored_apps WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun delete(profileId: Long, packageName: String)

    @Query("DELETE FROM monitored_apps WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("SELECT * FROM monitored_apps WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun get(profileId: Long, packageName: String): MonitoredAppEntity?
}

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits WHERE profileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: Long): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun get(profileId: Long, packageName: String): AppLimitEntity?

    @Upsert
    suspend fun upsert(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}

@Dao
interface ScheduleWindowDao {
    @Query("SELECT * FROM schedule_windows WHERE profileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<ScheduleWindowEntity>>

    @Query("SELECT * FROM schedule_windows WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: Long): List<ScheduleWindowEntity>

    @Query("SELECT * FROM schedule_windows")
    fun observeAll(): Flow<List<ScheduleWindowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: ScheduleWindowEntity): Long

    @Query("UPDATE schedule_windows SET segmentId = :segmentId WHERE id = :windowId")
    suspend fun setSegmentId(windowId: Long, segmentId: Long)

    @Query("DELETE FROM schedule_windows WHERE segmentId = :segmentId")
    suspend fun deleteForSegment(segmentId: Long)

    @Query("DELETE FROM schedule_windows WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM schedule_windows WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}

@Dao
interface PauseDao {
    @Query("SELECT * FROM pauses WHERE untilEpochMs > :now")
    fun observeActive(now: Long): Flow<List<PauseEntity>>

    @Insert
    suspend fun insert(pause: PauseEntity): Long

    @Query("DELETE FROM pauses WHERE untilEpochMs <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM pauses WHERE profileId = :profileId AND type = 'noLimitToday'")
    suspend fun deleteNoLimitTodayForProfile(profileId: Long)

    @Query("DELETE FROM pauses WHERE type = 'focusBlock' AND profileId IS NULL")
    suspend fun deleteGlobalFocusBlocks()

    @Query("DELETE FROM pauses WHERE type = 'focusBlock' AND profileId = :profileId")
    suspend fun deleteFocusBlocksForProfile(profileId: Long)

    @Query(
        "DELETE FROM pauses WHERE type NOT IN ('focusBlock', 'focusMode', 'emergencyBypass') AND profileId IS NULL",
    )
    suspend fun deleteGlobalAllowPauses()

    @Query(
        "DELETE FROM pauses WHERE type NOT IN ('focusBlock', 'focusMode', 'emergencyBypass') AND profileId = :profileId",
    )
    suspend fun deleteAllowPausesForProfile(profileId: Long)

    @Query("DELETE FROM pauses WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}

@Dao
interface UsageSessionDao {
    @Insert
    suspend fun insert(session: UsageSessionEntity): Long

    @Query("SELECT * FROM usage_sessions WHERE profileId = :profileId AND startEpochMs >= :from AND startEpochMs < :to")
    suspend fun getForPeriod(profileId: Long, from: Long, to: Long): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_sessions WHERE profileId = :profileId ORDER BY startEpochMs DESC LIMIT :limit")
    suspend fun getRecent(profileId: Long, limit: Int): List<UsageSessionEntity>

    @Query("DELETE FROM usage_sessions WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}

@Dao
interface UsageAggregateDao {
    @Upsert
    suspend fun upsertAll(aggregates: List<UsageAggregateEntity>)

    @Query(
        """
        SELECT * FROM usage_aggregates
        WHERE profileId = :profileId AND period = :period
        AND periodStart >= :from AND periodStart < :to
        """,
    )
    suspend fun getForPeriod(profileId: Long, period: String, from: Long, to: Long): List<UsageAggregateEntity>

    @Query(
        """
        SELECT COALESCE(SUM(totalMs), 0) FROM usage_aggregates
        WHERE profileId = :profileId AND packageName = :packageName
        AND period = :period AND periodStart = :periodStart
        """,
    )
    suspend fun getTotal(profileId: Long, packageName: String, period: String, periodStart: Long): Long

    @Query("DELETE FROM usage_aggregates WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}

@Dao
interface OverrideEventDao {
    @Insert
    suspend fun insert(event: OverrideEventEntity): Long

    @Query("SELECT COUNT(*) FROM override_events WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM override_events
        WHERE profileId = :profileId AND packageName = :packageName
        AND timestamp >= :dayStartMs AND method = 'extension'
        """,
    )
    suspend fun countExtensionOverridesForPackageToday(
        profileId: Long,
        packageName: String,
        dayStartMs: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM override_events
        WHERE profileId = :profileId AND timestamp >= :dayStartMs AND method = 'extension'
        """,
    )
    suspend fun countExtensionOverridesForProfileToday(
        profileId: Long,
        dayStartMs: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM override_events
        WHERE profileId = :profileId AND packageName = :packageName AND timestamp >= :dayStartMs
        """,
    )
    suspend fun countOverridesForPackageToday(
        profileId: Long,
        packageName: String,
        dayStartMs: Long,
    ): Int

    @Query(
        """
        SELECT COALESCE(SUM(extensionMs), 0) FROM override_events
        WHERE profileId = :profileId AND packageName = :packageName
        AND timestamp >= :sinceMs AND method = 'extension'
        """,
    )
    suspend fun sumExtensionMsForPackageSince(
        profileId: Long,
        packageName: String,
        sinceMs: Long,
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(extensionMs), 0) FROM override_events
        WHERE profileId = :profileId AND timestamp >= :sinceMs AND method = 'extension'
        """,
    )
    suspend fun sumExtensionMsForProfileSince(
        profileId: Long,
        sinceMs: Long,
    ): Long

    @Query(
        """
        DELETE FROM override_events
        WHERE profileId = :profileId AND timestamp >= :sinceMs AND method = 'extension'
        """,
    )
    suspend fun deleteExtensionOverridesForProfileSince(profileId: Long, sinceMs: Long)

    @Query(
        """
        SELECT * FROM override_events
        WHERE profileId = :profileId AND packageName = :packageName
        ORDER BY timestamp DESC LIMIT :limit
        """,
    )
    suspend fun getRecentOverridesForPackage(
        profileId: Long,
        packageName: String,
        limit: Int,
    ): List<OverrideEventEntity>

    @Query("SELECT * FROM override_events WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(profileId: Long, limit: Int): List<OverrideEventEntity>

    @Query("DELETE FROM override_events WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}

@Dao
interface SessionStateDao {
    @Query("SELECT * FROM session_state WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun get(profileId: Long, packageName: String): SessionStateEntity?

    @Upsert
    suspend fun upsert(state: SessionStateEntity)

    @Query("DELETE FROM session_state WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun delete(profileId: Long, packageName: String)

    @Query("DELETE FROM session_state WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}
