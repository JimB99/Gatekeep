package com.gatekeep.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
    val passwordHash: String? = null,
    val lockEnabled: Boolean = false,
    val sortOrder: Int = 0,
    val autoScheduleEnabled: Boolean = false,
    val defaultFrictionMethod: String = "math",
    val defaultFrictionDifficulty: String = "medium",
    val delayOpenSeconds: Int = 0,
    val gradualTighteningEnabled: Boolean = false,
    val gradualTighteningTargetDailyMs: Long? = null,
    val gradualTighteningPercentPerWeek: Int = 5,
)

@Entity(
    tableName = "monitored_apps",
    primaryKeys = ["profileId", "packageName"],
)
data class MonitoredAppEntity(
    val profileId: Long,
    val packageName: String,
    val label: String,
    val category: String = "other",
    val isWhitelistedEssential: Boolean = false,
)

@Entity(
    tableName = "app_limits",
    primaryKeys = ["profileId", "packageName"],
)
data class AppLimitEntity(
    val profileId: Long,
    val packageName: String,
    val dailyLimitMs: Long? = null,
    val weeklyLimitMs: Long? = null,
    val hourlyLimitMs: Long? = null,
    val sessionLimitMs: Long? = null,
    val breakDurationMs: Long? = null,
    val enabled: Boolean = true,
    val frictionMethod: String? = null,
    val frictionDifficulty: String? = null,
    val extensionMsOnBypass: Long = 300_000L,
)

@Entity(
    tableName = "schedule_windows",
    indices = [Index("profileId")],
)
data class ScheduleWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val packageName: String? = null,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val isProfileAutoSwitch: Boolean = false,
)

@Entity(
    tableName = "pauses",
    indices = [Index("profileId")],
)
data class PauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long? = null,
    val packageName: String? = null,
    val type: String,
    val untilEpochMs: Long,
)

@Entity(
    tableName = "usage_sessions",
    indices = [Index("profileId"), Index("packageName")],
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val profileId: Long,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationMs: Long,
)

@Entity(
    tableName = "usage_aggregates",
    indices = [Index(value = ["profileId", "packageName", "period", "periodStart"])],
)
data class UsageAggregateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val profileId: Long,
    val period: String,
    val periodStart: Long,
    val totalMs: Long,
)

@Entity(
    tableName = "override_events",
    indices = [Index("profileId")],
)
data class OverrideEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val profileId: Long,
    val timestamp: Long,
    val method: String,
    val extensionMs: Long,
)

@Entity(tableName = "session_state")
data class SessionStateEntity(
    @PrimaryKey val packageName: String,
    val profileId: Long,
    val sessionStartEpochMs: Long,
    val breakUntilEpochMs: Long? = null,
)
