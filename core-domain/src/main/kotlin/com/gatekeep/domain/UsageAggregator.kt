package com.gatekeep.domain

import com.gatekeep.domain.model.UsagePeriod
import java.time.ZoneId
import java.time.temporal.WeekFields

data class UsageSessionRecord(
    val packageName: String,
    val profileId: Long,
    val startEpochMs: Long,
    val endEpochMs: Long,
) {
    val durationMs: Long get() = (endEpochMs - startEpochMs).coerceAtLeast(0)
}

data class UsageAggregate(
    val packageName: String,
    val profileId: Long,
    val period: UsagePeriod,
    val periodStartEpochMs: Long,
    val totalMs: Long,
)

object UsageAggregator {

    fun aggregateSessions(
        sessions: List<UsageSessionRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        dayResetMinuteOfDay: Int = 0,
    ): List<UsageAggregate> {
        val dayBuckets = mutableMapOf<Triple<Long, String, Long>, Long>()
        val hourBuckets = mutableMapOf<Triple<Long, String, Long>, Long>()
        val weekBuckets = mutableMapOf<Triple<Long, String, Long>, Long>()

        for (session in sessions) {
            val dayStart = TimeBoundaries.dayStartEpochMs(
                session.startEpochMs,
                zoneId,
                dayResetMinuteOfDay,
            )
            val hourStart = TimeBoundaries.hourStartEpochMs(session.startEpochMs, zoneId)
            val weekStart = TimeBoundaries.weekBounds(
                session.startEpochMs,
                zoneId,
                WeekFields.ISO,
                dayResetMinuteOfDay,
            ).startMs

            val dayKey = Triple(session.profileId, session.packageName, dayStart)
            val hourKey = Triple(session.profileId, session.packageName, hourStart)
            val weekKey = Triple(session.profileId, session.packageName, weekStart)

            dayBuckets[dayKey] = (dayBuckets[dayKey] ?: 0) + session.durationMs
            hourBuckets[hourKey] = (hourBuckets[hourKey] ?: 0) + session.durationMs
            weekBuckets[weekKey] = (weekBuckets[weekKey] ?: 0) + session.durationMs
        }

        return buildList {
            dayBuckets.forEach { (key, total) ->
                add(UsageAggregate(key.second, key.first, UsagePeriod.day, key.third, total))
            }
            hourBuckets.forEach { (key, total) ->
                add(UsageAggregate(key.second, key.first, UsagePeriod.hour, key.third, total))
            }
            weekBuckets.forEach { (key, total) ->
                add(UsageAggregate(key.second, key.first, UsagePeriod.week, key.third, total))
            }
        }
    }

    fun dailyTotalForPackage(
        sessions: List<UsageSessionRecord>,
        profileId: Long,
        packageName: String,
        dayStartEpochMs: Long,
        dayEndEpochMs: Long,
    ): Long {
        return sessions
            .filter { it.profileId == profileId && it.packageName == packageName }
            .filter { it.startEpochMs >= dayStartEpochMs && it.startEpochMs < dayEndEpochMs }
            .sumOf { it.durationMs }
    }
}
