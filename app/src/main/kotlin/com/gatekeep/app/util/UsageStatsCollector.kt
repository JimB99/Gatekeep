package com.gatekeep.app.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.gatekeep.domain.model.UsageSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageStatsCollector(private val context: Context) {

    private val usageStatsManager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun queryEvents(fromMs: Long, toMs: Long): List<UsageEvent> {
        val events = mutableListOf<UsageEvent>()
        val usageEvents = usageStatsManager.queryEvents(fromMs, toMs)
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                -> events.add(UsageEvent(event.packageName, event.timeStamp, ForegroundEventType.RESUMED))
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                -> events.add(UsageEvent(event.packageName, event.timeStamp, ForegroundEventType.PAUSED))
            }
        }
        return events
    }

    fun getForegroundPackageFallback(): String? {
        val now = System.currentTimeMillis()
        return queryEvents(now - 60_000, now)
            .lastOrNull { it.type == ForegroundEventType.RESUMED }
            ?.packageName
    }

    fun usageMsForPackage(packageName: String, startMs: Long, endMs: Long): Long {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startMs,
            endMs,
        ) ?: return 0L
        return stats.filterIsInstance<UsageStats>()
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }

    fun getUsageSnapshot(packageName: String, nowMs: Long = System.currentTimeMillis()): UsageSnapshot {
        val dayStart = dayStartEpochMs(nowMs)
        val hourStart = hourStartEpochMs(nowMs)
        val weekStart = weekStartEpochMs(nowMs)
        return UsageSnapshot(
            dailyMs = usageMsForPackage(packageName, dayStart, nowMs),
            hourlyMs = usageMsForPackage(packageName, hourStart, nowMs),
            weeklyMs = usageMsForPackage(packageName, weekStart, nowMs),
        )
    }

    fun totalUsageForPackages(packageNames: Set<String>, startMs: Long, endMs: Long): Long {
        if (packageNames.isEmpty()) return 0L
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startMs,
            endMs,
        ) ?: return 0L
        return stats.filterIsInstance<UsageStats>()
            .filter { it.packageName in packageNames }
            .sumOf { it.totalTimeInForeground }
    }

    fun dayStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
            .toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun hourStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
            .withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

    fun weekStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        return zdt.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1)
            .toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}

data class UsageEvent(
    val packageName: String,
    val timestamp: Long,
    val type: ForegroundEventType,
)

enum class ForegroundEventType {
    RESUMED,
    PAUSED,
}
