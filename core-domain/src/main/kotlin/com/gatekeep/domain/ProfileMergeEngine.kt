package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.ResolvedSchedulePolicy
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow

object ProfileMergeEngine {

    private val modePriority = mapOf(
        SchedulePolicyMode.block to 4,
        SchedulePolicyMode.customize to 3,
        SchedulePolicyMode.default to 2,
        SchedulePolicyMode.allow to 1,
    )

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

    fun mergeProfileAndAppLimit(profile: Profile, packageName: String, perAppLimit: AppLimit?): AppLimit {
        val base = profile.toAppLimit(packageName)
        if (perAppLimit == null) return base
        return AppLimit(
            profileId = base.profileId,
            packageName = packageName,
            dailyLimitMs = perAppLimit.dailyLimitMs ?: base.dailyLimitMs,
            weeklyLimitMs = perAppLimit.weeklyLimitMs ?: base.weeklyLimitMs,
            hourlyLimitMs = perAppLimit.hourlyLimitMs ?: base.hourlyLimitMs,
            sessionLimitMs = perAppLimit.sessionLimitMs ?: base.sessionLimitMs,
            breakDurationMs = perAppLimit.breakDurationMs ?: base.breakDurationMs,
            enabled = perAppLimit.enabled,
            frictionMethod = perAppLimit.frictionMethod ?: base.frictionMethod,
            frictionDifficulty = perAppLimit.frictionDifficulty ?: base.frictionDifficulty,
            extensionMsOnBypass = perAppLimit.extensionMsOnBypass,
        )
    }

    fun sumUsageSnapshots(usages: List<com.gatekeep.domain.model.UsageSnapshot>): com.gatekeep.domain.model.UsageSnapshot {
        if (usages.isEmpty()) return com.gatekeep.domain.model.UsageSnapshot()
        return com.gatekeep.domain.model.UsageSnapshot(
            dailyMs = usages.sumOf { it.dailyMs },
            weeklyMs = usages.sumOf { it.weeklyMs },
            hourlyMs = usages.sumOf { it.hourlyMs },
        )
    }

    fun mergedSchedulePolicy(
        profiles: List<Profile>,
        segments: List<ScheduleSegment>,
        windows: List<ScheduleWindow>,
        packageName: String,
        nowEpochMs: Long,
    ): ResolvedSchedulePolicy {
        val perProfile = profiles.map { profile ->
            SchedulePolicyResolver.resolveForProfile(
                profile = profile,
                segments = segments,
                windows = windows,
                packageName = packageName,
                nowEpochMs = nowEpochMs,
            )
        }
        if (perProfile.isEmpty()) {
            return ResolvedSchedulePolicy(
                mode = SchedulePolicyMode.default,
                limits = null,
                enforcementConfig = null,
                source = com.gatekeep.domain.model.PolicySource.noScheduleMatch,
            )
        }
        val strictest = perProfile.maxBy { modePriority[it.mode] ?: 0 }
        if (strictest.mode == SchedulePolicyMode.allow || strictest.mode == SchedulePolicyMode.block) {
            return strictest
        }
        val limits = perProfile.mapNotNull { it.limits }
        val mergedLimit = mergedLimitForApp(limits, packageName)
        val mergedConfig = mergeEnforcementConfigs(
            perProfile.mapNotNull { it.enforcementConfig },
        )
        return strictest.copy(
            limits = mergedLimit,
            enforcementConfig = mergedConfig ?: strictest.enforcementConfig,
        )
    }

    private fun mergeEnforcementConfigs(configs: List<ProfileEnforcementConfig>): ProfileEnforcementConfig? {
        if (configs.isEmpty()) return null
        if (configs.size == 1) return configs.first()
        return configs.first()
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
