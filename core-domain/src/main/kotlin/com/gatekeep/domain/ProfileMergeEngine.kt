package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.ScheduleWindow

object ProfileMergeEngine {

    fun mergedLimitForApp(limits: List<AppLimit>, packageName: String): AppLimit? {
        val applicable = limits.filter { it.packageName == packageName && it.enabled }
        if (applicable.isEmpty()) return null

        return AppLimit(
            profileId = applicable.first().profileId,
            packageName = packageName,
            dailyLimitMs = applicable.mapNotNull { it.dailyLimitMs }.minOrNull(),
            weeklyLimitMs = applicable.mapNotNull { it.weeklyLimitMs }.minOrNull(),
            hourlyLimitMs = applicable.mapNotNull { it.hourlyLimitMs }.minOrNull(),
            sessionLimitMs = applicable.mapNotNull { it.sessionLimitMs }.minOrNull(),
            breakDurationMs = applicable.map { it.breakDurationMs }.filterNotNull().maxOrNull(),
            enabled = true,
            frictionMethod = applicable.firstNotNullOfOrNull { it.frictionMethod }
                ?: applicable.first().frictionMethod,
            extensionMsOnBypass = applicable.minOf { it.extensionMsOnBypass },
        )
    }

    fun mergedScheduleWindows(windows: List<ScheduleWindow>): List<ScheduleWindow> = windows

    fun isWithinMergedSchedule(
        windows: List<ScheduleWindow>,
        packageName: String,
        profileIds: Set<Long>,
        nowEpochMs: Long,
    ): Boolean {
        val applicable = windows.filter { window ->
            window.profileId in profileIds && !window.isProfileAutoSwitch
        }
        if (applicable.isEmpty()) return true

        return applicable.any { window ->
            ScheduleEvaluator.isWithinAllowedWindow(
                windows = listOf(window),
                packageName = packageName,
                profileId = window.profileId,
                nowEpochMs = nowEpochMs,
            )
        }
    }

    fun mergeUsageSnapshots(usages: List<com.gatekeep.domain.model.UsageSnapshot>): com.gatekeep.domain.model.UsageSnapshot {
        if (usages.isEmpty()) return com.gatekeep.domain.model.UsageSnapshot()
        return com.gatekeep.domain.model.UsageSnapshot(
            dailyMs = usages.maxOf { it.dailyMs },
            weeklyMs = usages.maxOf { it.weeklyMs },
            hourlyMs = usages.maxOf { it.hourlyMs },
        )
    }
}
