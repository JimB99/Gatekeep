package com.gatekeep.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gatekeep.app.enforcement.GatekeepNotificationHelper
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.UsageAggregator
import com.gatekeep.domain.UsageSessionRecord
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageRepository,
    private val profileRepository: ProfileRepository,
    private val notificationHelper: GatekeepNotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profile = profileRepository.observeActiveProfile().first() ?: return Result.success()
        val sessions = usageRepository.getRecentSessions(profile.id, 500)
        val records = sessions.map {
            UsageSessionRecord(it.packageName, it.profileId, it.startEpochMs, it.endEpochMs)
        }
        usageRepository.aggregateAndStore(records)
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notificationHelper.showWarning(
            "Weekly Gatekeep Report",
            "Review your screen time stats in the app.",
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "weekly_report"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
