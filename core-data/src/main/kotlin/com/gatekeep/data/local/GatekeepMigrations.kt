package com.gatekeep.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object GatekeepMigrations {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profiles ADD COLUMN limitWaitDurationSeconds INTEGER NOT NULL DEFAULT 60",
            )
            db.execSQL(
                "ALTER TABLE profiles ADD COLUMN limitBreakDurationMs INTEGER DEFAULT NULL",
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE session_state ADD COLUMN pendingWaitUntilEpochMs INTEGER DEFAULT NULL",
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE session_state ADD COLUMN sessionLimitNotified INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profiles ADD COLUMN noScheduleMatchMode TEXT NOT NULL DEFAULT 'default'",
            )
            db.execSQL("ALTER TABLE profiles ADD COLUMN noScheduleMatchDailyLimitMs INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN noScheduleMatchHourlyLimitMs INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN noScheduleMatchWeeklyLimitMs INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN noScheduleMatchSessionLimitMs INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN noScheduleMatchOnOpenAction TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN noScheduleMatchOnLimitAction TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN noScheduleMatchOnSessionLimitAction TEXT DEFAULT NULL")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS schedule_segments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    profileId INTEGER NOT NULL,
                    label TEXT,
                    isActive INTEGER NOT NULL,
                    mode TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    dailyLimitMs INTEGER,
                    hourlyLimitMs INTEGER,
                    weeklyLimitMs INTEGER,
                    sessionLimitMs INTEGER,
                    onOpenAction TEXT,
                    onLimitAction TEXT,
                    onSessionLimitAction TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_segments_profileId ON schedule_segments(profileId)")

            db.execSQL("ALTER TABLE schedule_windows ADD COLUMN segmentId INTEGER DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_windows_segmentId ON schedule_windows(segmentId)")

            migrateWindowsToSegments(db)
        }

        private fun migrateWindowsToSegments(db: SupportSQLiteDatabase) {
            val profileIds = mutableListOf<Long>()
            db.query("SELECT id FROM profiles").use { cursor ->
                while (cursor.moveToNext()) {
                    profileIds.add(cursor.getLong(0))
                }
            }

            for (profileId in profileIds) {
                val enforcementCount = db.query(
                    "SELECT COUNT(*) FROM schedule_windows WHERE profileId = ? AND isProfileAutoSwitch = 0",
                    arrayOf(profileId.toString()),
                ).use { cursor ->
                    if (cursor.moveToNext()) cursor.getInt(0) else 0
                }

                if (enforcementCount > 0) {
                    db.execSQL(
                        "UPDATE profiles SET noScheduleMatchMode = 'block' WHERE id = ?",
                        arrayOf(profileId),
                    )
                }

                val windows = mutableListOf<Triple<Long, Int, Pair<Int, Int>>>()
                db.query(
                    """
                    SELECT id, dayOfWeek, startMinute, endMinute
                    FROM schedule_windows
                    WHERE profileId = ? AND isProfileAutoSwitch = 0
                    """.trimIndent(),
                    arrayOf(profileId.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        windows.add(
                            Triple(
                                cursor.getLong(0),
                                cursor.getInt(1),
                                cursor.getInt(2) to cursor.getInt(3),
                            ),
                        )
                    }
                }

                val groups = windows.groupBy { it.third }
                var sortOrder = 0
                for ((timeRange, group) in groups) {
                    db.execSQL(
                        """
                        INSERT INTO schedule_segments (profileId, isActive, mode, sortOrder)
                        VALUES (?, 1, 'default', ?)
                        """.trimIndent(),
                        arrayOf(profileId, sortOrder++),
                    )
                    val segmentId = db.query("SELECT last_insert_rowid()").use { c ->
                        c.moveToFirst()
                        c.getLong(0)
                    }
                    for ((windowId, _, _) in group) {
                        db.execSQL(
                            "UPDATE schedule_windows SET segmentId = ? WHERE id = ?",
                            arrayOf(segmentId, windowId),
                        )
                    }
                }
            }
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN limitExtensionPolicyJson TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN sessionExtensionPolicyJson TEXT DEFAULT NULL")
            db.execSQL(
                """
                UPDATE profiles
                SET limitExtensionPolicyJson = extensionPolicyJson,
                    sessionExtensionPolicyJson = extensionPolicyJson
                WHERE extensionPolicyJson IS NOT NULL
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profiles ADD COLUMN limitUsageScope TEXT NOT NULL DEFAULT 'perApp'",
            )
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_state_new (
                    profileId INTEGER NOT NULL,
                    packageName TEXT NOT NULL,
                    sessionStartEpochMs INTEGER NOT NULL,
                    breakUntilEpochMs INTEGER,
                    excludedMs INTEGER NOT NULL DEFAULT 0,
                    frictionStartedAtEpochMs INTEGER,
                    pendingWaitUntilEpochMs INTEGER,
                    sessionLimitNotified INTEGER NOT NULL DEFAULT 0,
                    consecutiveExtensionCount INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(profileId, packageName)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO session_state_new (
                    profileId, packageName, sessionStartEpochMs, breakUntilEpochMs,
                    excludedMs, frictionStartedAtEpochMs, pendingWaitUntilEpochMs,
                    sessionLimitNotified, consecutiveExtensionCount
                )
                SELECT
                    profileId, packageName, sessionStartEpochMs, breakUntilEpochMs,
                    excludedMs, frictionStartedAtEpochMs, pendingWaitUntilEpochMs,
                    sessionLimitNotified, 0
                FROM session_state
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE session_state")
            db.execSQL("ALTER TABLE session_state_new RENAME TO session_state")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_override_events_profile_package_method_timestamp
                ON override_events(profileId, packageName, method, timestamp)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_override_events_profile_method_timestamp
                ON override_events(profileId, method, timestamp)
                """.trimIndent(),
            )
        }
    }
}
