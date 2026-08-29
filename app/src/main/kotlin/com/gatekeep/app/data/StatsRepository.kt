package com.gatekeep.app.data

import android.content.Context
import com.gatekeep.app.R
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.StreakCalculator
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
        get() = context.resources.configuration.locales[0]

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
            val dailyLimit = allProfiles.find { it.id == profileId }?.dailyLimitMs
            for (app in apps) {
                val usageMs = usageByPackage[app.packageName] ?: 0L
                val existing = byPackage[app.packageName]
                if (existing == null) {
                    byPackage[app.packageName] = AppUsageStat(
                        packageName = app.packageName,
                        label = app.label,
                        usageMs = usageMs,
                        limitMs = dailyLimit,
                    )
                } else {
                    byPackage[app.packageName] = existing.copy(
                        limitMs = TrackedAppMerge.mergeDailyLimit(existing.limitMs, dailyLimit),
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
        val underBudget = (6 downTo 0).map { daysAgo ->
            val dayStart = usageStatsCollector.dayStartEpochMs(now - daysAgo * 86_400_000L)
            val dayEnd = dayStart + 86_400_000L
            val used = usageStatsCollector.totalUsageForPackages(packages, dayStart, minOf(dayEnd, now))
            used <= dailyCap
        }
        return StreakCalculator.calculate(underBudget)
    }

    suspend fun overrideCount(profileId: Long): Int =
        usageRepository.getOverrideCount(profileId)

    suspend fun profileUsageSummary(profileId: Long, profileName: String): ProfileUsageSummary {
        val apps = trackedAppsForRange(profileId, StatsTimeRange.SingleDay(System.currentTimeMillis()))
        return ProfileUsageSummary(
            profileId = profileId,
            profileName = profileName,
            appCount = apps.size,
            totalUsageMs = apps.sumOf { it.usageMs },
            apps = apps,
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
                val start = usageStatsCollector.dayStartEpochMs(range.dayEpochMs)
                val end = start + 86_400_000L
                val now = System.currentTimeMillis()
                val buckets = (0 until 24).map { hour ->
                    val slotStart = start + hour * 3_600_000L
                    val slotEnd = if (hour == 23) end else start + (hour + 1) * 3_600_000L
                    val effectiveEnd = if (slotStart < now) minOf(slotEnd, now) else slotStart
                    ChartBucket(
                        label = "%02d".format(hour),
                        usageMs = 0L,
                        startMs = slotStart,
                        endMs = effectiveEnd.coerceAtLeast(slotStart),
                    )
                }
                RangeBounds(start, minOf(end, now), buckets)
            }
            is StatsTimeRange.Week -> {
                val weekFields = WeekFields.ISO
                val startDate = LocalDate.of(range.year, 1, 1)
                    .with(weekFields.weekOfWeekBasedYear(), range.weekOfYear.toLong())
                    .with(weekFields.dayOfWeek(), 1)
                val start = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = minOf(start + 7 * 86_400_000L, now)
                val buckets = (0 until 7).mapNotNull { offset ->
                    val dayStart = start + offset * 86_400_000L
                    if (dayStart >= end) return@mapNotNull null
                    val dayEnd = minOf(dayStart + 86_400_000L, end)
                    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dayStart), zoneId)
                    val dayName = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale)
                    val dayNum = zdt.dayOfMonth.toString()
                    ChartBucket(
                        label = dayName,
                        usageMs = 0L,
                        startMs = dayStart,
                        endMs = dayEnd,
                        subLabel = dayNum,
                    )
                }
                RangeBounds(start, end, buckets)
            }
            is StatsTimeRange.Month -> {
                val start = usageStatsCollector.monthStartEpochMs(range.year, range.month)
                val nextMonth = if (range.month == 12) {
                    usageStatsCollector.monthStartEpochMs(range.year + 1, 1)
                } else {
                    usageStatsCollector.monthStartEpochMs(range.year, range.month + 1)
                }
                val end = minOf(nextMonth, now)
                val daysInMonth = ((end - start) / 86_400_000L).toInt().coerceAtLeast(1)
                val buckets = (0 until daysInMonth).map { dayOffset ->
                    val dayStart = start + dayOffset * 86_400_000L
                    val dayEnd = minOf(dayStart + 86_400_000L, end)
                    val dayNum = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dayStart), zoneId).dayOfMonth
                    ChartBucket("$dayNum", 0L, dayStart, dayEnd)
                }
                RangeBounds(start, end, buckets)
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

    fun formatRangeLabel(range: StatsTimeRange): String = when (range) {
        is StatsTimeRange.SingleDay -> {
            val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(range.dayEpochMs), zoneId)
            val today = ZonedDateTime.now(zoneId).toLocalDate()
            val date = zdt.toLocalDate()
            val formatted = date.toString()
            if (date == today) "$formatted ${context.getString(R.string.today_suffix)}" else formatted
        }
        is StatsTimeRange.Week -> context.getString(
            R.string.week_label_format,
            range.weekOfYear,
            range.year,
        )
        is StatsTimeRange.Month -> {
            val monthName = ZonedDateTime.of(range.year, range.month, 1, 0, 0, 0, 0, zoneId)
                .month.getDisplayName(TextStyle.FULL, appLocale)
            val monthNum = "%02d".format(range.month)
            "$monthName ($monthNum/${range.year})"
        }
        is StatsTimeRange.Year -> range.year.toString()
    }
}
