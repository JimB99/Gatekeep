package com.gatekeep.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gatekeep.data.local.dao.AppLimitDao
import com.gatekeep.data.local.dao.MonitoredAppDao
import com.gatekeep.data.local.dao.OverrideEventDao
import com.gatekeep.data.local.dao.PauseDao
import com.gatekeep.data.local.dao.ProfileDao
import com.gatekeep.data.local.dao.ScheduleWindowDao
import com.gatekeep.data.local.dao.SessionStateDao
import com.gatekeep.data.local.dao.UsageAggregateDao
import com.gatekeep.data.local.dao.UsageSessionDao
import com.gatekeep.data.local.entity.AppLimitEntity
import com.gatekeep.data.local.entity.MonitoredAppEntity
import com.gatekeep.data.local.entity.OverrideEventEntity
import com.gatekeep.data.local.entity.PauseEntity
import com.gatekeep.data.local.entity.ProfileEntity
import com.gatekeep.data.local.entity.ScheduleWindowEntity
import com.gatekeep.data.local.entity.SessionStateEntity
import com.gatekeep.data.local.entity.UsageAggregateEntity
import com.gatekeep.data.local.entity.UsageSessionEntity

@Database(
    entities = [
        ProfileEntity::class,
        MonitoredAppEntity::class,
        AppLimitEntity::class,
        ScheduleWindowEntity::class,
        PauseEntity::class,
        UsageSessionEntity::class,
        UsageAggregateEntity::class,
        OverrideEventEntity::class,
        SessionStateEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class GatekeepDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun scheduleWindowDao(): ScheduleWindowDao
    abstract fun pauseDao(): PauseDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun usageAggregateDao(): UsageAggregateDao
    abstract fun overrideEventDao(): OverrideEventDao
    abstract fun sessionStateDao(): SessionStateDao
}
