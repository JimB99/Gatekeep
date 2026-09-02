package com.gatekeep.app.di

import android.content.Context
import com.gatekeep.data.local.DatabaseBootstrap
import com.gatekeep.data.local.GatekeepDatabase
import com.gatekeep.app.enforcement.CountdownController
import com.gatekeep.app.enforcement.ExtensionGrantUseCase
import com.gatekeep.app.enforcement.SessionLifecycleService
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        enforcementLog: EnforcementLog,
    ): GatekeepDatabase {
        enforcementLog.clearStaleMigrationErrors()
        return DatabaseBootstrap.open(context) { error ->
            enforcementLog.logError("Database migration failed; recreating local database", error)
        }
    }

    @Provides
    @Singleton
    fun provideProfileRepository(db: GatekeepDatabase): ProfileRepository =
        ProfileRepository(
            db.profileDao(),
            db.monitoredAppDao(),
            db.appLimitDao(),
            db.scheduleSegmentDao(),
            db.scheduleWindowDao(),
            db.pauseDao(),
            db.usageSessionDao(),
            db.usageAggregateDao(),
            db.overrideEventDao(),
            db.sessionStateDao(),
        )

    @Provides
    @Singleton
    fun provideUsageRepository(db: GatekeepDatabase): UsageRepository =
        UsageRepository(
            db.usageSessionDao(),
            db.usageAggregateDao(),
            db.sessionStateDao(),
            db.pauseDao(),
            db.overrideEventDao(),
        )

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideUsageStatsCollector(@ApplicationContext context: Context): UsageStatsCollector =
        UsageStatsCollector(context)

    @Provides
    @Singleton
    fun provideExtensionGrantUseCase(usageRepository: UsageRepository): ExtensionGrantUseCase =
        ExtensionGrantUseCase(usageRepository)

    @Provides
    @Singleton
    fun provideSessionLifecycleService(usageRepository: UsageRepository): SessionLifecycleService =
        SessionLifecycleService(usageRepository)

    @Provides
    @Singleton
    fun provideCountdownController(): CountdownController = CountdownController()

    @Provides
    @Singleton
    fun provideEnforcementLog(@ApplicationContext context: Context): EnforcementLog =
        EnforcementLog(context)
}
