package com.gatekeep.domain

import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.ResolvedSchedulePolicy
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object PolicyTimelineResolver {

    data class EffectivePolicySnapshot(
        val activeSegmentLabel: String?,
        val modeLabel: String,
        val dailyLimitMs: Long?,
        val hourlyLimitMs: Long?,
        val weeklyLimitMs: Long?,
        val sessionLimitMs: Long?,
        val graceRemainingMs: Long?,
        val noLimitToday: Boolean,
        val nextChangeLabel: String?,
    )

    data class UpcomingPolicyChange(
        val segmentId: Long?,
        val segmentLabel: String?,
        val modeLabel: String,
        val startsAtEpochMs: Long,
    )

    fun snapshot(
        profile: Profile,
        policy: ResolvedSchedulePolicy,
        segments: List<ScheduleSegment>,
        pauses: List<Pause>,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): EffectivePolicySnapshot {
        val activeSegment = segments.find { it.id == policy.activeSegmentId }
        val gracePause = pauses.filter {
            it.profileId == profile.id &&
                it.type.name == "extensionGrace" &&
                it.untilEpochMs > nowEpochMs
        }.maxByOrNull { it.untilEpochMs }
        val noLimit = pauses.any {
            it.profileId == profile.id &&
                it.type.name == "noLimitToday" &&
                it.untilEpochMs > nowEpochMs
        }
        return EffectivePolicySnapshot(
            activeSegmentLabel = activeSegment?.label,
            modeLabel = policy.mode.name,
            dailyLimitMs = policy.limits?.dailyLimitMs,
            hourlyLimitMs = policy.limits?.hourlyLimitMs,
            weeklyLimitMs = policy.limits?.weeklyLimitMs,
            sessionLimitMs = policy.limits?.sessionLimitMs,
            graceRemainingMs = gracePause?.let { it.untilEpochMs - nowEpochMs },
            noLimitToday = noLimit,
            nextChangeLabel = findNextChange(segments, emptyList(), nowEpochMs, zoneId)?.let {
                formatUpcoming(it, zoneId)
            },
        )
    }

    fun findNextChange(
        segments: List<ScheduleSegment>,
        windows: List<ScheduleWindow>,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): UpcomingPolicyChange? {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMs), zoneId)
        val minuteOfDay = now.hour * 60 + now.minute
        val dayOfWeek = now.dayOfWeek.value % 7
        val candidates = windows.mapNotNull { window ->
            val segment = segments.find { it.id == window.segmentId } ?: return@mapNotNull null
            if (!segment.isActive) return@mapNotNull null
            val startsIn = minutesUntilWindowStart(dayOfWeek, minuteOfDay, window.dayOfWeek, window.startMinute)
            if (startsIn <= 0) return@mapNotNull null
            UpcomingPolicyChange(
                segmentId = segment.id,
                segmentLabel = segment.label,
                modeLabel = segment.mode.name,
                startsAtEpochMs = nowEpochMs + startsIn * 60_000L,
            )
        }
        return candidates.minByOrNull { it.startsAtEpochMs }
    }

    private fun minutesUntilWindowStart(
        currentDay: Int,
        currentMinute: Int,
        windowDay: Int,
        startMinute: Int,
    ): Int {
        val currentTotal = currentDay * 24 * 60 + currentMinute
        val windowTotal = windowDay * 24 * 60 + startMinute
        return if (windowTotal > currentTotal) windowTotal - currentTotal
        else windowTotal + 7 * 24 * 60 - currentTotal
    }

    private fun formatUpcoming(change: UpcomingPolicyChange, zoneId: ZoneId): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(change.startsAtEpochMs), zoneId)
        val time = zdt.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val label = change.segmentLabel ?: change.modeLabel
        return "$label @ $time"
    }
}
