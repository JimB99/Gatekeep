package com.gatekeep.app.di

import android.content.Context
import androidx.room.Room
import com.gatekeep.data.local.GatekeepDatabase
import com.gatekeep.data.local.GatekeepMigrations
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
    fun provideDatabase(@ApplicationContext context: Context): GatekeepDatabase =
        Room.databaseBuilder(context, GatekeepDatabase::class.java, "gatekeep.db")
            .addMigrations(
                GatekeepMigrations.MIGRATION_5_6,
                GatekeepMigrations.MIGRATION_6_7,
                GatekeepMigrations.MIGRATION_7_8,
            )
            .build()

    @Provides
    @Singleton
    fun provideProfileRepository(db: GatekeepDatabase): ProfileRepository =
        ProfileRepository(
            db.profileDao(),
            db.monitoredAppDao(),
            db.appLimitDao(),
            db.scheduleWindowDao(),
            db.pauseDao(),
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
    fun provideEnforcementLog(@ApplicationContext context: Context): EnforcementLog =
        EnforcementLog(context)
}
