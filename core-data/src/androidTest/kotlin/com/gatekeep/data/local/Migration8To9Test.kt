package com.gatekeep.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration8To9Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GatekeepDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate8To9_matchesRoomSchema() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    isActive INTEGER NOT NULL,
                    passwordHash TEXT,
                    lockEnabled INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    autoScheduleEnabled INTEGER NOT NULL,
                    defaultFrictionMethod TEXT NOT NULL,
                    defaultFrictionDifficulty TEXT NOT NULL,
                    delayOpenSeconds INTEGER NOT NULL,
                    gradualTighteningEnabled INTEGER NOT NULL,
                    gradualTighteningTargetDailyMs INTEGER,
                    gradualTighteningPercentPerWeek INTEGER NOT NULL,
                    dailyLimitMs INTEGER,
                    hourlyLimitMs INTEGER,
                    weeklyLimitMs INTEGER,
                    sessionLimitMs INTEGER,
                    breakDurationMs INTEGER,
                    openWaitDurationSeconds INTEGER NOT NULL,
                    sessionWaitDurationSeconds INTEGER NOT NULL,
                    limitWaitDurationSeconds INTEGER NOT NULL,
                    limitBreakDurationMs INTEGER,
                    onOpenAction TEXT NOT NULL,
                    onLimitAction TEXT NOT NULL,
                    onSessionLimitAction TEXT NOT NULL,
                    extensionPolicyJson TEXT
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS schedule_windows (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    profileId INTEGER NOT NULL,
                    packageName TEXT,
                    dayOfWeek INTEGER NOT NULL,
                    startMinute INTEGER NOT NULL,
                    endMinute INTEGER NOT NULL,
                    isProfileAutoSwitch INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS index_schedule_windows_profileId ON schedule_windows(profileId)")
            execSQL("INSERT INTO profiles (name, isActive, lockEnabled, sortOrder, autoScheduleEnabled, defaultFrictionMethod, defaultFrictionDifficulty, delayOpenSeconds, gradualTighteningEnabled, gradualTighteningPercentPerWeek, openWaitDurationSeconds, sessionWaitDurationSeconds, limitWaitDurationSeconds, onOpenAction, onLimitAction, onSessionLimitAction) VALUES ('Work', 1, 0, 0, 0, 'math', 'medium', 0, 0, 5, 60, 60, 60, 'none', 'limitWithExtensions', 'limitWithExtensions')")
            execSQL("INSERT INTO schedule_windows (profileId, dayOfWeek, startMinute, endMinute, isProfileAutoSwitch) VALUES (1, 1, 540, 1020, 0)")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, GatekeepMigrations.MIGRATION_8_9)
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
