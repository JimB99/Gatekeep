package com.gatekeep.app.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.gatekeep.domain.UsageBucketAggregator
import com.gatekeep.domain.model.UsageSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

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

    fun foregroundMsInRange(
        startMs: Long,
        endMs: Long,
        packageFilter: Set<String>? = null,
    ): Long {
        if (endMs <= startMs) return 0L
        val events = queryEvents(startMs, endMs)
        val lastResumed = mutableMapOf<String, Long>()
        var total = 0L
        for (event in events.sortedBy { it.timestamp }) {
            if (packageFilter != null && event.packageName !in packageFilter) continue
            if (isExcludedPackage(event.packageName)) continue
            when (event.type) {
                ForegroundEventType.RESUMED -> lastResumed[event.packageName] = event.timestamp
                ForegroundEventType.PAUSED -> {
                    val resumedAt = lastResumed.remove(event.packageName) ?: continue
                    total += clipDuration(resumedAt, event.timestamp, startMs, endMs)
                }
            }
        }
        val now = System.currentTimeMillis().coerceAtMost(endMs)
        for ((pkg, resumedAt) in lastResumed) {
            if (packageFilter == null || pkg in packageFilter) {
                total += clipDuration(resumedAt, now, startMs, endMs)
            }
        }
        return total.coerceAtLeast(0L)
    }

    fun foregroundMsByPackageInRange(startMs: Long, endMs: Long): Map<String, Long> {
        if (endMs <= startMs) return emptyMap()
        val events = queryEvents(startMs, endMs)
        val lastResumed = mutableMapOf<String, Long>()
        val totals = mutableMapOf<String, Long>()
        for (event in events.sortedBy { it.timestamp }) {
            if (isExcludedPackage(event.packageName)) continue
            when (event.type) {
                ForegroundEventType.RESUMED -> lastResumed[event.packageName] = event.timestamp
                ForegroundEventType.PAUSED -> {
                    val resumedAt = lastResumed.remove(event.packageName) ?: continue
                    val duration = clipDuration(resumedAt, event.timestamp, startMs, endMs)
                    totals[event.packageName] = totals.getOrDefault(event.packageName, 0L) + duration
                }
            }
        }
        val now = System.currentTimeMillis().coerceAtMost(endMs)
        for ((pkg, resumedAt) in lastResumed) {
            val duration = clipDuration(resumedAt, now, startMs, endMs)
            totals[pkg] = totals.getOrDefault(pkg, 0L) + duration
        }
        return totals
    }

    private fun clipDuration(resumedAt: Long, pausedAt: Long, rangeStart: Long, rangeEnd: Long): Long {
        val start = maxOf(resumedAt, rangeStart)
        val end = minOf(pausedAt, rangeEnd)
        return (end - start).coerceAtLeast(0L)
    }

    fun usageMsForPackage(packageName: String, startMs: Long, endMs: Long): Long =
        foregroundMsInRange(startMs, endMs, setOf(packageName))

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

    fun totalUsageForPackages(packageNames: Set<String>, startMs: Long, endMs: Long): Long =
        foregroundMsInRange(startMs, endMs, packageNames)

    fun dayStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
            .toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun hourStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
            .withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

    fun weekStartEpochMs(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        return zdt.with(WeekFields.ISO.dayOfWeek(), 1)
            .toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun monthStartEpochMs(year: Int, month: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.of(year, month, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun yearStartEpochMs(year: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun isExcludedPackage(packageName: String): Boolean =
        packageName == context.packageName ||
            packageName.startsWith("com.android.") ||
            packageName == "com.google.android.inputmethod.latin" ||
            packageName == "com.samsung.android.honeyboard" ||
            packageName == "com.touchtype.swiftkey"

    fun totalDeviceUsageMs(startMs: Long, endMs: Long): Long =
        foregroundMsInRange(startMs, endMs, packageFilter = null)

    fun deviceUsageMsForBuckets(
        rangeStartMs: Long,
        rangeEndMs: Long,
        bucketStarts: LongArray,
        bucketEnds: LongArray,
    ): LongArray {
        if (rangeEndMs <= rangeStartMs || bucketStarts.isEmpty()) {
            return LongArray(bucketStarts.size)
        }
        val sessions = foregroundSessionsInRange(rangeStartMs, rangeEndMs, packageFilter = null)
        return UsageBucketAggregator.allocateSessionsToBuckets(sessions, bucketStarts, bucketEnds)
    }

    private fun foregroundSessionsInRange(
        startMs: Long,
        endMs: Long,
        packageFilter: Set<String>?,
    ): List<UsageBucketAggregator.ForegroundSession> {
        if (endMs <= startMs) return emptyList()
        val events = queryEvents(startMs, endMs)
        val lastResumed = mutableMapOf<String, Long>()
        val sessions = mutableListOf<UsageBucketAggregator.ForegroundSession>()
        for (event in events.sortedBy { it.timestamp }) {
            if (packageFilter != null && event.packageName !in packageFilter) continue
            if (isExcludedPackage(event.packageName)) continue
            when (event.type) {
                ForegroundEventType.RESUMED -> lastResumed[event.packageName] = event.timestamp
                ForegroundEventType.PAUSED -> {
                    val resumedAt = lastResumed.remove(event.packageName) ?: continue
                    val sessionStart = maxOf(resumedAt, startMs)
                    val sessionEnd = minOf(event.timestamp, endMs)
                    if (sessionEnd > sessionStart) {
                        sessions.add(UsageBucketAggregator.ForegroundSession(sessionStart, sessionEnd))
                    }
                }
            }
        }
        val now = System.currentTimeMillis().coerceAtMost(endMs)
        for ((pkg, resumedAt) in lastResumed) {
            if (packageFilter != null && pkg !in packageFilter) continue
            val sessionStart = maxOf(resumedAt, startMs)
            val sessionEnd = minOf(now, endMs)
            if (sessionEnd > sessionStart) {
                sessions.add(UsageBucketAggregator.ForegroundSession(sessionStart, sessionEnd))
            }
        }
        return sessions
    }

    fun topApps(startMs: Long, endMs: Long, limit: Int = 5): List<Pair<String, Long>> =
        foregroundMsByPackageInRange(startMs, endMs)
            .filter { (_, ms) -> ms > 0 }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

    fun labelForPackage(packageName: String): String =
        runCatching {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
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
