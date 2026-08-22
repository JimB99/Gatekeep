package com.gatekeep.data.backup

import com.gatekeep.data.local.entity.AppLimitEntity
import com.gatekeep.data.local.entity.MonitoredAppEntity
import com.gatekeep.data.local.entity.ProfileEntity
import com.gatekeep.data.local.entity.ScheduleWindowEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProfileBackup(
    val profile: ProfileBackupData,
    val monitoredApps: List<MonitoredAppBackup>,
    val limits: List<AppLimitBackup>,
    val scheduleWindows: List<ScheduleWindowBackup>,
)

@Serializable
data class ProfileBackupData(
    val name: String,
    val lockEnabled: Boolean,
    val autoScheduleEnabled: Boolean,
    val defaultFrictionMethod: String,
    val defaultFrictionDifficulty: String,
    val delayOpenSeconds: Int,
    val gradualTighteningEnabled: Boolean,
    val gradualTighteningTargetDailyMs: Long?,
    val gradualTighteningPercentPerWeek: Int,
)

@Serializable
data class MonitoredAppBackup(
    val packageName: String,
    val label: String,
    val category: String,
    val isWhitelistedEssential: Boolean,
)

@Serializable
data class AppLimitBackup(
    val packageName: String,
    val dailyLimitMs: Long?,
    val weeklyLimitMs: Long?,
    val hourlyLimitMs: Long?,
    val sessionLimitMs: Long?,
    val breakDurationMs: Long?,
    val enabled: Boolean,
    val frictionMethod: String?,
    val frictionDifficulty: String?,
    val extensionMsOnBypass: Long,
)

@Serializable
data class ScheduleWindowBackup(
    val packageName: String?,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val isProfileAutoSwitch: Boolean,
)

@Serializable
data class GatekeepBackup(
    val version: Int = 1,
    val profiles: List<ProfileBackup>,
)

object BackupManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun export(profiles: List<ProfileBackup>): String =
        json.encodeToString(GatekeepBackup(profiles = profiles))

    fun import(data: String): GatekeepBackup =
        json.decodeFromString<GatekeepBackup>(data)

    fun profileToBackup(
        profile: ProfileEntity,
        apps: List<MonitoredAppEntity>,
        limits: List<AppLimitEntity>,
        windows: List<ScheduleWindowEntity>,
    ) = ProfileBackup(
        profile = ProfileBackupData(
            name = profile.name,
            lockEnabled = profile.lockEnabled,
            autoScheduleEnabled = profile.autoScheduleEnabled,
            defaultFrictionMethod = profile.defaultFrictionMethod,
            defaultFrictionDifficulty = profile.defaultFrictionDifficulty,
            delayOpenSeconds = profile.delayOpenSeconds,
            gradualTighteningEnabled = profile.gradualTighteningEnabled,
            gradualTighteningTargetDailyMs = profile.gradualTighteningTargetDailyMs,
            gradualTighteningPercentPerWeek = profile.gradualTighteningPercentPerWeek,
        ),
        monitoredApps = apps.map {
            MonitoredAppBackup(it.packageName, it.label, it.category, it.isWhitelistedEssential)
        },
        limits = limits.map {
            AppLimitBackup(
                it.packageName, it.dailyLimitMs, it.weeklyLimitMs, it.hourlyLimitMs,
                it.sessionLimitMs, it.breakDurationMs, it.enabled,
                it.frictionMethod, it.frictionDifficulty, it.extensionMsOnBypass,
            )
        },
        scheduleWindows = windows.map {
            ScheduleWindowBackup(
                it.packageName, it.dayOfWeek, it.startMinute, it.endMinute, it.isProfileAutoSwitch,
            )
        },
    )
}
