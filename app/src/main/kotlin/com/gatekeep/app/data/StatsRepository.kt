package com.gatekeep.app.data

import android.content.Context
import com.gatekeep.app.util.appLocale
import com.gatekeep.app.util.withAppLocale
import com.gatekeep.app.R
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.StreakCalculator
import com.gatekeep.domain.TimeBoundaries
import com.gatekeep.domain.TimeRange
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.TrackedAppMerge
import com.gatekeep.domain.UsageBucketAggregator
import com.gatekeep.domain.model.StreakInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AppUsageStat(
    val packageName: String,
    val label: String,
    val usageMs: Long,
    val limitMs: Long?,
)

data class ProfileUsageSummary(
    val profileId: Long,
    val profileName: String,
    val appCount: Int,
    val totalUsageMs: Long,
    val apps: List<AppUsageStat>,
)

data class StatsOverview(
    val totalUsageMs: Long,
    val chartBuckets: List<ChartBucket>,
    val rangeLabel: String,
    val scaleMs: Long,
)

@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val usageRepository: UsageRepository,
    private val usageStatsCollector: UsageStatsCollector,
) {
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val appLocale: Locale
        get() = context.appLocale()

    suspend fun overviewForRange(range: StatsTimeRange): StatsOverview {
        val bounds = buildRangeBounds(range)
        val bucketStarts = bounds.buckets.map { it.startMs }.toLongArray()
        val bucketEnds = bounds.buckets.map { it.endMs }.toLongArray()
        val usageByBucket = usageStatsCollector.deviceUsageMsForBuckets(
            bounds.startMs,
            bounds.endMs,
            bucketStarts,
            bucketEnds,
        )
        val buckets = bounds.buckets.mapIndexed { index, bucket ->
            bucket.copy(usageMs = usageByBucket[index])
        }
        return StatsOverview(
            totalUsageMs = usageByBucket.sum(),
            chartBuckets = buckets,
            rangeLabel = formatRangeLabel(range),
            scaleMs = UsageBucketAggregator.computeScaleMs(usageByBucket),
        )
    }

    suspend fun topAppsForRange(range: StatsTimeRange, limit: Int = 10): List<TopAppUsage> {
        val bounds = buildRangeBounds(range)
        return usageStatsCollector.topApps(bounds.startMs, bounds.endMs, limit).map { (pkg, ms) ->
            TopAppUsage(
                packageName = pkg,
                label = usageStatsCollector.labelForPackage(pkg),
                usageMs = ms,
            )
        }
    }

    suspend fun trackedAppsForRange(profileId: Long, range: StatsTimeRange): List<AppUsageStat> =
        trackedAppsForProfiles(listOf(profileId), range)

    suspend fun trackedAppsForProfiles(profileIds: List<Long>, range: StatsTimeRange): List<AppUsageStat> {
        if (profileIds.isEmpty()) return emptyList()
        val bounds = buildRangeBounds(range)
        val usageByPackage = usageStatsCollector.foregroundMsByPackageInRange(bounds.startMs, bounds.endMs)
        val allProfiles = profileRepository.observeProfiles().first()
        val byPackage = linkedMapOf<String, AppUsageStat>()
        for (profileId in profileIds) {
            val apps = profileRepository.observeMonitoredApps(profileId).first()
            val profile = allProfiles.find { it.id == profileId }
            val rangeLimitMs = when (range) {
                is StatsTimeRange.SingleDay -> profile?.dailyLimitMs
                is StatsTimeRange.Week -> profile?.weeklyLimitMs
                else -> null
            }
            for (app in apps) {
                val usageMs = usageByPackage[app.packageName] ?: 0L
                val existing = byPackage[app.packageName]
                if (existing == null) {
                    byPackage[app.packageName] = AppUsageStat(
                        packageName = app.packageName,
                        label = app.label,
                        usageMs = usageMs,
                        limitMs = rangeLimitMs,
                    )
                } else {
                    byPackage[app.packageName] = existing.copy(
                        usageMs = maxOf(existing.usageMs, usageMs),
                        limitMs = when (range) {
                            is StatsTimeRange.SingleDay -> TrackedAppMerge.mergeDailyLimit(existing.limitMs, rangeLimitMs)
                            is StatsTimeRange.Week -> TrackedAppMerge.mergeDailyLimit(existing.limitMs, rangeLimitMs)
                            else -> null
                        },
                    )
                }
            }
        }
        return byPackage.values.sortedByDescending { it.usageMs }
    }

    suspend fun streakForProfile(profileId: Long): StreakInfo {
        val profile = profileRepository.observeProfiles().first().find { it.id == profileId }
        val dailyCap = profile?.dailyLimitMs ?: return StreakInfo(0, 0, null)
        val packages = profileRepository.observeMonitoredApps(profileId).first().map { it.packageName }.toSet()
        if (packages.isEmpty()) return StreakInfo(0, 0, null)
        val now = System.currentTimeMillis()
        val todayStart = usageStatsCollector.dayStartEpochMs(now)
        val underBudget = (6 downTo 0).map { daysAgo ->
            val dayBounds = TimeBoundaries.dayOffsetsFrom(todayStart, -daysAgo, zoneId)
            val used = usageStatsCollector.totalUsageForPackages(
                packages,
                dayBounds.startMs,
                minOf(dayBounds.endExclusiveMs, now),
            )
            used <= dailyCap
        }
        return StreakCalculator.calculate(underBudget)
    }

    suspend fun overrideCount(profileId: Long): Int =
        usageRepository.getOverrideCount(profileId)

    suspend fun profileUsageSummary(profileId: Long, profileName: String): ProfileUsageSummary {
        val profile = profileRepository.observeProfiles().first().find { it.id == profileId }
        val apps = trackedAppsForRange(profileId, StatsTimeRange.SingleDay(System.currentTimeMillis()))
        val totalUsageMs = apps.sumOf { it.usageMs }
        val displayApps = if (profile?.limitUsageScope == LimitUsageScope.sharedPool) {
            val sharedLimit = profile.dailyLimitMs
            listOf(
                AppUsageStat(
                    packageName = "",
                    label = profile.name,
                    usageMs = totalUsageMs,
                    limitMs = sharedLimit,
                ),
            )
        } else {
            apps
        }
        return ProfileUsageSummary(
            profileId = profileId,
            profileName = profileName,
            appCount = apps.size,
            totalUsageMs = totalUsageMs,
            apps = displayApps,
        )
    }

    private data class RangeBounds(
        val startMs: Long,
        val endMs: Long,
        val buckets: List<ChartBucket>,
    )

    private fun buildRangeBounds(range: StatsTimeRange): RangeBounds {
        val now = System.currentTimeMillis()
        return when (range) {
            is StatsTimeRange.SingleDay -> {
                val dayStart = usageStatsCollector.dayStartEpochMs(range.dayEpochMs)
                val dayBounds = TimeBoundaries.dayBounds(range.dayEpochMs, zoneId)
                val nowMs = System.currentTimeMillis()
                val buckets = TimeBoundaries.iterateHoursInDay(dayStart, zoneId).mapIndexed { hour, slot ->
                    val effectiveEnd = if (slot.startMs < nowMs) minOf(slot.endExclusiveMs, nowMs) else slot.startMs
                    ChartBucket(
                        label = "%02d".format(hour),
                        usageMs = 0L,
                        startMs = slot.startMs,
                        endMs = effectiveEnd.coerceAtLeast(slot.startMs),
                    )
                }
                RangeBounds(dayBounds.startMs, minOf(dayBounds.endExclusiveMs, nowMs), buckets)
            }
            is StatsTimeRange.Week -> {
                val weekFields = WeekFields.of(appLocale)
                val startDate = LocalDate.of(range.year, 1, 1)
                    .with(weekFields.weekOfWeekBasedYear(), range.weekOfYear.toLong())
                    .with(weekFields.dayOfWeek(), 1)
                val anchorMs = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val weekBounds = TimeBoundaries.weekBounds(anchorMs, zoneId, weekFields)
                val end = minOf(weekBounds.endExclusiveMs, now)
                val buckets = TimeBoundaries.iterateDaysInRange(
                    TimeRange(weekBounds.startMs, end),
                    zoneId,
                ).map { day ->
                    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(day.startMs), zoneId)
                    val dayName = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale)
                    val dayNum = zdt.dayOfMonth.toString()
                    ChartBucket(
                        label = dayName,
                        usageMs = 0L,
                        startMs = day.startMs,
                        endMs = day.endExclusiveMs,
                        subLabel = dayNum,
                    )
                }
                RangeBounds(weekBounds.startMs, end, buckets)
            }
            is StatsTimeRange.Month -> {
                val monthBounds = TimeBoundaries.monthBounds(range.year, range.month, zoneId)
                val end = minOf(monthBounds.endExclusiveMs, now)
                val buckets = TimeBoundaries.iterateDaysInRange(
                    TimeRange(monthBounds.startMs, end),
                    zoneId,
                ).map { day ->
                    val dayNum = ZonedDateTime.ofInstant(Instant.ofEpochMilli(day.startMs), zoneId).dayOfMonth
                    ChartBucket("$dayNum", 0L, day.startMs, day.endExclusiveMs)
                }
                RangeBounds(monthBounds.startMs, end, buckets)
            }
            is StatsTimeRange.Year -> {
                val start = usageStatsCollector.yearStartEpochMs(range.year)
                val end = minOf(usageStatsCollector.yearStartEpochMs(range.year + 1), now)
                val buckets = (1..12).mapNotNull { month ->
                    val monthStart = usageStatsCollector.monthStartEpochMs(range.year, month)
                    if (monthStart >= end) return@mapNotNull null
                    val monthEnd = minOf(
                        if (month == 12) usageStatsCollector.yearStartEpochMs(range.year + 1)
                        else usageStatsCollector.monthStartEpochMs(range.year, month + 1),
                        end,
                    )
                    val label = ZonedDateTime.ofInstant(Instant.ofEpochMilli(monthStart), zoneId)
                        .month.getDisplayName(TextStyle.SHORT, appLocale)
                    ChartBucket(label, 0L, monthStart, monthEnd)
                }
                RangeBounds(start, end, buckets)
            }
        }
    }

    fun formatRangeLabel(range: StatsTimeRange): String {
        val localized = context.withAppLocale()
        return when (range) {
            is StatsTimeRange.SingleDay -> {
                val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(range.dayEpochMs), zoneId)
                val today = ZonedDateTime.now(zoneId).toLocalDate()
                val date = zdt.toLocalDate()
                if (date == today) {
                    localized.getString(R.string.stats_period_today)
                } else {
                    date.format(
                        java.time.format.DateTimeFormatter
                            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                            .withLocale(appLocale),
                    )
                }
            }
            is StatsTimeRange.Week -> {
                val weekFields = java.time.temporal.WeekFields.of(appLocale)
                val startDate = LocalDate.of(range.year, 1, 1)
                    .with(weekFields.weekOfWeekBasedYear(), range.weekOfYear.toLong())
                    .with(weekFields.dayOfWeek(), 1)
                val endDate = startDate.plusDays(6)
                val formatter = java.time.format.DateTimeFormatter
                    .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                    .withLocale(appLocale)
                localized.getString(
                    R.string.stats_week_range_format,
                    startDate.format(formatter),
                    endDate.format(formatter),
                )
            }
            is StatsTimeRange.Month -> {
                val monthName = ZonedDateTime.of(range.year, range.month, 1, 0, 0, 0, 0, zoneId)
                    .month.getDisplayName(TextStyle.FULL, appLocale)
                val monthNum = "%02d".format(range.month)
                "$monthName ($monthNum/${range.year})"
            }
            is StatsTimeRange.Year -> range.year.toString()
        }
    }
}
