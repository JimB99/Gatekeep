package com.gatekeep.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gatekeep.app.R
import com.gatekeep.app.enforcement.GatekeepNotificationHelper
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.data.repository.AppSettings
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.UsageSessionRecord
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageRepository,
    private val profileRepository: ProfileRepository,
    private val usageStatsCollector: UsageStatsCollector,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profiles = profileRepository.observeActiveProfiles().first()
        if (profiles.isEmpty()) return Result.success()

        val dayStart = usageStatsCollector.dayStartEpochMs()
        val now = System.currentTimeMillis()

        profiles.forEach { profile ->
            val apps = profileRepository.observeMonitoredApps(profile.id).first()
            apps.forEach { app ->
                val usageMs = usageStatsCollector.usageMsForPackage(app.packageName, dayStart, now)
                if (usageMs > 0) {
                    usageRepository.recordSession(
                        packageName = app.packageName,
                        profileId = profile.id,
                        startEpochMs = dayStart,
                        endEpochMs = dayStart + usageMs,
                    )
                }
            }
            val sessions = usageRepository.getRecentSessions(profile.id, 500)
            val records = sessions.map {
                UsageSessionRecord(it.packageName, it.profileId, it.startEpochMs, it.endEpochMs)
            }
            usageRepository.aggregateAndStore(records)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "usage_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: GatekeepNotificationHelper,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.weeklyReportEnabled) return Result.success()

        notificationHelper.showWarning(
            applicationContext.getString(R.string.weekly_report_title),
            applicationContext.getString(R.string.weekly_report_body),
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "weekly_report"

        fun schedule(context: Context, settings: AppSettings = AppSettings()) {
            if (!settings.weeklyReportEnabled) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }
            val delayMs = delayUntilNextWeeklyReport(
                dayOfWeek = settings.weeklyReportDayOfWeek,
                minuteOfDay = settings.weeklyReportMinuteOfDay,
            )
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun delayUntilNextWeeklyReport(
            dayOfWeek: Int,
            minuteOfDay: Int,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): Long {
            val now = ZonedDateTime.now(zoneId)
            val hour = minuteOfDay / 60
            val minute = minuteOfDay % 60
            val currentDow = now.dayOfWeek.value % 7
            val targetDow = dayOfWeek.coerceIn(0, 6)
            var daysToAdd = (targetDow - currentDow + 7) % 7
            var candidate = now.plusDays(daysToAdd.toLong())
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusWeeks(1)
            }
            return Duration.between(now, candidate).toMillis().coerceAtLeast(0L)
        }
    }
}
