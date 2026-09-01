package com.gatekeep.data.backup

import com.gatekeep.data.local.entity.AppLimitEntity
import com.gatekeep.data.local.entity.MonitoredAppEntity
import com.gatekeep.data.local.entity.ProfileEntity
import com.gatekeep.data.local.entity.ScheduleSegmentEntity
import com.gatekeep.data.local.entity.ScheduleWindowEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProfileBackup(
    val profile: ProfileBackupData,
    val monitoredApps: List<MonitoredAppBackup>,
    val limits: List<AppLimitBackup>,
    val scheduleSegments: List<ScheduleSegmentBackup> = emptyList(),
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
    val onOpenAction: String = "none",
    val onLimitAction: String = "limitWithExtensions",
    val onSessionLimitAction: String = "limitWithExtensions",
    val extensionPolicyJson: String? = null,
    val limitExtensionPolicyJson: String? = null,
    val sessionExtensionPolicyJson: String? = null,
    val noScheduleMatchMode: String = "default",
    val noScheduleMatchDailyLimitMs: Long? = null,
    val noScheduleMatchHourlyLimitMs: Long? = null,
    val noScheduleMatchWeeklyLimitMs: Long? = null,
    val noScheduleMatchSessionLimitMs: Long? = null,
    val noScheduleMatchOnOpenAction: String? = null,
    val noScheduleMatchOnLimitAction: String? = null,
    val noScheduleMatchOnSessionLimitAction: String? = null,
    val dailyLimitMs: Long? = null,
    val hourlyLimitMs: Long? = null,
    val weeklyLimitMs: Long? = null,
    val sessionLimitMs: Long? = null,
    val breakDurationMs: Long? = null,
    val limitBreakDurationMs: Long? = null,
    val openWaitDurationSeconds: Int = 60,
    val sessionWaitDurationSeconds: Int = 60,
    val limitWaitDurationSeconds: Int = 60,
    val limitUsageScope: String = "perApp",
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
data class ScheduleSegmentBackup(
    val localId: Long,
    val label: String? = null,
    val isActive: Boolean = true,
    val mode: String = "default",
    val sortOrder: Int = 0,
    val dailyLimitMs: Long? = null,
    val hourlyLimitMs: Long? = null,
    val weeklyLimitMs: Long? = null,
    val sessionLimitMs: Long? = null,
    val onOpenAction: String? = null,
    val onLimitAction: String? = null,
    val onSessionLimitAction: String? = null,
)

@Serializable
data class ScheduleWindowBackup(
    val segmentLocalId: Long? = null,
    val packageName: String?,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val isProfileAutoSwitch: Boolean,
)

@Serializable
data class GatekeepBackup(
    val version: Int = 3,
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
        segments: List<ScheduleSegmentEntity>,
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
            onOpenAction = profile.onOpenAction,
            onLimitAction = profile.onLimitAction,
            onSessionLimitAction = profile.onSessionLimitAction,
            extensionPolicyJson = profile.limitExtensionPolicyJson ?: profile.extensionPolicyJson,
            limitExtensionPolicyJson = profile.limitExtensionPolicyJson ?: profile.extensionPolicyJson,
            sessionExtensionPolicyJson = profile.sessionExtensionPolicyJson ?: profile.extensionPolicyJson,
            noScheduleMatchMode = profile.noScheduleMatchMode,
            noScheduleMatchDailyLimitMs = profile.noScheduleMatchDailyLimitMs,
            noScheduleMatchHourlyLimitMs = profile.noScheduleMatchHourlyLimitMs,
            noScheduleMatchWeeklyLimitMs = profile.noScheduleMatchWeeklyLimitMs,
            noScheduleMatchSessionLimitMs = profile.noScheduleMatchSessionLimitMs,
            noScheduleMatchOnOpenAction = profile.noScheduleMatchOnOpenAction,
            noScheduleMatchOnLimitAction = profile.noScheduleMatchOnLimitAction,
            noScheduleMatchOnSessionLimitAction = profile.noScheduleMatchOnSessionLimitAction,
            dailyLimitMs = profile.dailyLimitMs,
            hourlyLimitMs = profile.hourlyLimitMs,
            weeklyLimitMs = profile.weeklyLimitMs,
            sessionLimitMs = profile.sessionLimitMs,
            breakDurationMs = profile.breakDurationMs,
            limitBreakDurationMs = profile.limitBreakDurationMs,
            openWaitDurationSeconds = profile.openWaitDurationSeconds,
            sessionWaitDurationSeconds = profile.sessionWaitDurationSeconds,
            limitWaitDurationSeconds = profile.limitWaitDurationSeconds,
            limitUsageScope = profile.limitUsageScope,
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
        scheduleSegments = segments.map {
            ScheduleSegmentBackup(
                localId = it.id,
                label = it.label,
                isActive = it.isActive,
                mode = it.mode,
                sortOrder = it.sortOrder,
                dailyLimitMs = it.dailyLimitMs,
                hourlyLimitMs = it.hourlyLimitMs,
                weeklyLimitMs = it.weeklyLimitMs,
                sessionLimitMs = it.sessionLimitMs,
                onOpenAction = it.onOpenAction,
                onLimitAction = it.onLimitAction,
                onSessionLimitAction = it.onSessionLimitAction,
            )
        },
        scheduleWindows = windows.map {
            ScheduleWindowBackup(
                segmentLocalId = it.segmentId,
                packageName = it.packageName,
                dayOfWeek = it.dayOfWeek,
                startMinute = it.startMinute,
                endMinute = it.endMinute,
                isProfileAutoSwitch = it.isProfileAutoSwitch,
            )
        },
    )
}
