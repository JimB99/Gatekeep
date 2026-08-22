package com.gatekeep.app.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                events.add(UsageEvent(event.packageName, event.timeStamp, ForegroundEventType.RESUMED))
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                events.add(UsageEvent(event.packageName, event.timeStamp, ForegroundEventType.PAUSED))
            }
        }
        return events
    }

    fun getForegroundPackageFallback(): String? {
        val now = System.currentTimeMillis()
        val events = queryEvents(now - 60_000, now)
        return events.lastOrNull { it.type == ForegroundEventType.RESUMED }?.packageName
    }

    fun dayStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun hourStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
            .withMinute(0).withSecond(0).withNano(0)
            .toInstant()
            .toEpochMilli()
    }

    fun weekStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        return zdt.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
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
