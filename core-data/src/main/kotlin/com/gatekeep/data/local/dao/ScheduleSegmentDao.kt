package com.gatekeep.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gatekeep.data.local.entity.ScheduleSegmentEntity
import com.gatekeep.data.local.entity.ScheduleWindowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleSegmentDao {
    @Query("SELECT * FROM schedule_segments WHERE profileId = :profileId ORDER BY sortOrder, id")
    fun observeForProfile(profileId: Long): Flow<List<ScheduleSegmentEntity>>

    @Query("SELECT * FROM schedule_segments WHERE profileId = :profileId ORDER BY sortOrder, id")
    suspend fun getForProfile(profileId: Long): List<ScheduleSegmentEntity>

    @Query("SELECT * FROM schedule_segments")
    fun observeAll(): Flow<List<ScheduleSegmentEntity>>

    @Query("SELECT * FROM schedule_segments WHERE id = :id")
    suspend fun getById(id: Long): ScheduleSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: ScheduleSegmentEntity): Long

    @Update
    suspend fun update(segment: ScheduleSegmentEntity)

    @Query("DELETE FROM schedule_segments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM schedule_segments WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}
