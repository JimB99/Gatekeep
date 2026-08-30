package com.gatekeep.domain

import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.PolicySource
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ProfileEnforcementConfig
import com.gatekeep.domain.model.ResolvedSchedulePolicy
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object SchedulePolicyResolver {

    private val modePriority = mapOf(
        SchedulePolicyMode.block to 4,
        SchedulePolicyMode.customize to 3,
        SchedulePolicyMode.default to 2,
        SchedulePolicyMode.allow to 1,
    )

    fun resolveForProfile(
        profile: Profile,
        segments: List<ScheduleSegment>,
        windows: List<ScheduleWindow>,
        packageName: String,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ResolvedSchedulePolicy {
        val enforcementWindows = windows.filter {
            it.profileId == profile.id && !it.isProfileAutoSwitch
        }
        val activeSegments = segments.filter { it.profileId == profile.id && it.isActive }
        val matching = activeSegments.filter { segment ->
            segmentMatchesNow(segment.id, enforcementWindows, nowEpochMs, zoneId)
        }

        return if (matching.isEmpty()) {
            resolveMode(
                profile = profile,
                packageName = packageName,
                mode = profile.noScheduleMatchMode,
                overrides = profile.noScheduleMatchOverrides,
                activeSegmentId = null,
                source = PolicySource.noScheduleMatch,
            )
        } else {
            val winning = matching.maxBy { modePriority[it.mode] ?: 0 }
            val overrides = when (winning.mode) {
                SchedulePolicyMode.customize -> winning.overrides
                SchedulePolicyMode.default -> SchedulePolicyOverrides()
                else -> SchedulePolicyOverrides()
            }
            resolveMode(
                profile = profile,
                packageName = packageName,
                mode = winning.mode,
                overrides = overrides,
                activeSegmentId = winning.id,
                source = PolicySource.segment,
            )
        }
    }

    fun segmentMatchesNow(
        segmentId: Long,
        windows: List<ScheduleWindow>,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val segmentWindows = windows.filter { it.segmentId == segmentId }
        if (segmentWindows.isEmpty()) return false

        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMs), zoneId)
        val dayOfWeek = now.dayOfWeek.value % 7
        val minuteOfDay = now.hour * 60 + now.minute

        return segmentWindows.any { window ->
            ScheduleWindowMatcher.matchesWindow(window, dayOfWeek, minuteOfDay)
        }
    }

    fun isAppAvailable(policy: ResolvedSchedulePolicy): Boolean =
        policy.mode != SchedulePolicyMode.block

    private fun resolveMode(
        profile: Profile,
        packageName: String,
        mode: SchedulePolicyMode,
        overrides: SchedulePolicyOverrides,
        activeSegmentId: Long?,
        source: PolicySource,
    ): ResolvedSchedulePolicy = when (mode) {
        SchedulePolicyMode.allow -> ResolvedSchedulePolicy(
            mode = mode,
            limits = null,
            enforcementConfig = null,
            activeSegmentId = activeSegmentId,
            source = source,
        )
        SchedulePolicyMode.block -> ResolvedSchedulePolicy(
            mode = mode,
            limits = null,
            enforcementConfig = null,
            activeSegmentId = activeSegmentId,
            source = source,
        )
        SchedulePolicyMode.default -> ResolvedSchedulePolicy(
            mode = mode,
            limits = profile.toAppLimit(packageName),
            enforcementConfig = profile.enforcementConfig(),
            activeSegmentId = activeSegmentId,
            source = source,
        )
        SchedulePolicyMode.customize -> {
            val merged = mergeOverrides(profile, overrides)
            ResolvedSchedulePolicy(
                mode = mode,
                limits = merged.toAppLimit(packageName),
                enforcementConfig = merged.enforcementConfig(),
                activeSegmentId = activeSegmentId,
                source = source,
            )
        }
    }

    fun mergeOverrides(profile: Profile, overrides: SchedulePolicyOverrides): Profile =
        profile.copy(
            dailyLimitMs = overrides.dailyLimitMs ?: profile.dailyLimitMs,
            hourlyLimitMs = overrides.hourlyLimitMs ?: profile.hourlyLimitMs,
            weeklyLimitMs = overrides.weeklyLimitMs ?: profile.weeklyLimitMs,
            sessionLimitMs = overrides.sessionLimitMs ?: profile.sessionLimitMs,
            onOpenAction = overrides.onOpenAction ?: profile.onOpenAction,
            onLimitAction = overrides.onLimitAction ?: profile.onLimitAction,
            onSessionLimitAction = overrides.onSessionLimitAction ?: profile.onSessionLimitAction,
        )
}
