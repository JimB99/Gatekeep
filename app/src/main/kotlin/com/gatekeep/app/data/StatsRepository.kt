package com.gatekeep.app.data

import android.content.Context
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.StreakCalculator
import com.gatekeep.domain.model.StreakInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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

@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val usageRepository: UsageRepository,
    private val usageStatsCollector: UsageStatsCollector,
) {
    suspend fun profileUsageSummary(profileId: Long, profileName: String): ProfileUsageSummary {
        val apps = profileRepository.observeMonitoredApps(profileId).first()
        val profile = profileRepository.observeProfiles().first().find { it.id == profileId }
        val dailyLimit = profile?.dailyLimitMs
        val dayStart = usageStatsCollector.dayStartEpochMs()
        val now = System.currentTimeMillis()
        val appStats = apps.map { app ->
            AppUsageStat(
                packageName = app.packageName,
                label = app.label,
                usageMs = usageStatsCollector.usageMsForPackage(app.packageName, dayStart, now),
                limitMs = dailyLimit,
            )
        }
        return ProfileUsageSummary(
            profileId = profileId,
            profileName = profileName,
            appCount = apps.size,
            totalUsageMs = appStats.sumOf { it.usageMs },
            apps = appStats.sortedByDescending { it.usageMs },
        )
    }

    suspend fun weeklyUsageByDay(profileId: Long): List<Long> {
        val apps = profileRepository.observeMonitoredApps(profileId).first()
        val packages = apps.map { it.packageName }.toSet()
        if (packages.isEmpty()) return List(7) { 0L }
        val now = System.currentTimeMillis()
        return (6 downTo 0).map { daysAgo ->
            val dayStart = usageStatsCollector.dayStartEpochMs(now - daysAgo * 86_400_000L)
            val dayEnd = dayStart + 86_400_000L
            usageStatsCollector.totalUsageForPackages(packages, dayStart, minOf(dayEnd, now))
        }
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

    suspend fun overrideCount(profileId: Long) = usageRepository.getOverrideCount(profileId)
}
