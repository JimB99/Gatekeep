package com.gatekeep.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Builds historical GateKeep databases from explicit DDL.
 *
 * Schema export was only enabled at version 13, so `MigrationTestHelper.createDatabase(name, n)`
 * cannot load a bundle for older versions. These fixtures stand in for those bundles: they create
 * the on-disk shape a real device had at a given version, so migrations can be exercised and the
 * final state validated against the exported v13 schema.
 */
object LegacySchemaFixtures {

    fun createVersion8(context: Context, dbName: String): Unit =
        create(context, dbName, version = 8) { db ->
            db.execSQL(PROFILES_V8)
            db.execSQL(SCHEDULE_WINDOWS_V8)
            db.execSQL(INDEX_SCHEDULE_WINDOWS_PROFILE_ID)
            createStableTables(db)
            db.execSQL(SESSION_STATE_V11)
            db.execSQL(OVERRIDE_EVENTS)
            db.execSQL(INDEX_OVERRIDE_EVENTS_PROFILE_ID)
        }

    fun createVersion11(context: Context, dbName: String): Unit =
        create(context, dbName, version = 11) { db ->
            db.execSQL(PROFILES_V13)
            db.execSQL(SCHEDULE_SEGMENTS)
            db.execSQL(INDEX_SCHEDULE_SEGMENTS_PROFILE_ID)
            db.execSQL(SCHEDULE_WINDOWS_V13)
            db.execSQL(INDEX_SCHEDULE_WINDOWS_PROFILE_ID)
            db.execSQL(INDEX_SCHEDULE_WINDOWS_SEGMENT_ID)
            createStableTables(db)
            db.execSQL(SESSION_STATE_V11)
            db.execSQL(OVERRIDE_EVENTS)
            db.execSQL(INDEX_OVERRIDE_EVENTS_PROFILE_ID)
        }

    fun createVersion12(context: Context, dbName: String): Unit =
        create(context, dbName, version = 12) { db ->
            db.execSQL(PROFILES_V13)
            db.execSQL(SCHEDULE_SEGMENTS)
            db.execSQL(INDEX_SCHEDULE_SEGMENTS_PROFILE_ID)
            db.execSQL(SCHEDULE_WINDOWS_V13)
            db.execSQL(INDEX_SCHEDULE_WINDOWS_PROFILE_ID)
            db.execSQL(INDEX_SCHEDULE_WINDOWS_SEGMENT_ID)
            createStableTables(db)
            db.execSQL(SESSION_STATE_V12)
            db.execSQL(OVERRIDE_EVENTS)
            db.execSQL(INDEX_OVERRIDE_EVENTS_PROFILE_ID)
        }

    fun insertOverrideEvent(
        context: Context,
        dbName: String,
        packageName: String,
        profileId: Long,
        timestamp: Long,
        method: String,
        extensionMs: Long,
    ) = withDatabase(context, dbName) { db ->
        db.execSQL(
            """
            INSERT INTO override_events (packageName, profileId, timestamp, method, extensionMs)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(packageName, profileId, timestamp, method, extensionMs),
        )
    }

    fun insertSessionStateV11(
        context: Context,
        dbName: String,
        profileId: Long,
        packageName: String,
        sessionStartEpochMs: Long,
        excludedMs: Long = 0,
    ) = withDatabase(context, dbName) { db ->
        db.execSQL(
            """
            INSERT INTO session_state (
                profileId, packageName, sessionStartEpochMs, excludedMs, sessionLimitNotified
            ) VALUES (?, ?, ?, ?, 0)
            """.trimIndent(),
            arrayOf<Any>(profileId, packageName, sessionStartEpochMs, excludedMs),
        )
    }

    fun insertProfileV8(context: Context, dbName: String, name: String) =
        withDatabase(context, dbName) { db ->
            db.execSQL(
                """
                INSERT INTO profiles (
                    name, isActive, lockEnabled, sortOrder, autoScheduleEnabled,
                    defaultFrictionMethod, defaultFrictionDifficulty, delayOpenSeconds,
                    gradualTighteningEnabled, gradualTighteningPercentPerWeek,
                    openWaitDurationSeconds, sessionWaitDurationSeconds, limitWaitDurationSeconds,
                    onOpenAction, onLimitAction, onSessionLimitAction
                ) VALUES (?, 1, 0, 0, 0, 'math', 'medium', 0, 0, 5, 60, 60, 60,
                    'none', 'limitWithExtensions', 'limitWithExtensions')
                """.trimIndent(),
                arrayOf<Any>(name),
            )
        }

    fun insertScheduleWindowV8(
        context: Context,
        dbName: String,
        profileId: Long,
        dayOfWeek: Int,
        startMinute: Int,
        endMinute: Int,
    ) = withDatabase(context, dbName) { db ->
        db.execSQL(
            """
            INSERT INTO schedule_windows (profileId, dayOfWeek, startMinute, endMinute, isProfileAutoSwitch)
            VALUES (?, ?, ?, ?, 0)
            """.trimIndent(),
            arrayOf<Any>(profileId, dayOfWeek, startMinute, endMinute),
        )
    }

    private fun create(
        context: Context,
        dbName: String,
        version: Int,
        block: (SQLiteDatabase) -> Unit,
    ) {
        context.deleteDatabase(dbName)
        withDatabase(context, dbName) { db ->
            block(db)
            db.version = version
        }
    }

    private fun withDatabase(context: Context, dbName: String, block: (SQLiteDatabase) -> Unit) {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    /** Tables untouched by migrations 8 through 13. */
    private fun createStableTables(db: SQLiteDatabase) {
        db.execSQL(MONITORED_APPS)
        db.execSQL(APP_LIMITS)
        db.execSQL(PAUSES)
        db.execSQL(INDEX_PAUSES_PROFILE_ID)
        db.execSQL(USAGE_SESSIONS)
        db.execSQL(INDEX_USAGE_SESSIONS_PROFILE_ID)
        db.execSQL(INDEX_USAGE_SESSIONS_PACKAGE_NAME)
        db.execSQL(USAGE_AGGREGATES)
        db.execSQL(INDEX_USAGE_AGGREGATES)
    }

    private val PROFILES_V8 = """
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
    """.trimIndent()

    private val PROFILES_V13 = """
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
            extensionPolicyJson TEXT,
            limitExtensionPolicyJson TEXT,
            sessionExtensionPolicyJson TEXT,
            noScheduleMatchMode TEXT NOT NULL DEFAULT 'default',
            noScheduleMatchDailyLimitMs INTEGER,
            noScheduleMatchHourlyLimitMs INTEGER,
            noScheduleMatchWeeklyLimitMs INTEGER,
            noScheduleMatchSessionLimitMs INTEGER,
            noScheduleMatchOnOpenAction TEXT,
            noScheduleMatchOnLimitAction TEXT,
            noScheduleMatchOnSessionLimitAction TEXT,
            limitUsageScope TEXT NOT NULL DEFAULT 'perApp'
        )
    """.trimIndent()

    private val SCHEDULE_SEGMENTS = """
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
    """.trimIndent()

    private val SCHEDULE_WINDOWS_V8 = """
        CREATE TABLE IF NOT EXISTS schedule_windows (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            profileId INTEGER NOT NULL,
            packageName TEXT,
            dayOfWeek INTEGER NOT NULL,
            startMinute INTEGER NOT NULL,
            endMinute INTEGER NOT NULL,
            isProfileAutoSwitch INTEGER NOT NULL
        )
    """.trimIndent()

    private val SCHEDULE_WINDOWS_V13 = """
        CREATE TABLE IF NOT EXISTS schedule_windows (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            profileId INTEGER NOT NULL,
            segmentId INTEGER,
            packageName TEXT,
            dayOfWeek INTEGER NOT NULL,
            startMinute INTEGER NOT NULL,
            endMinute INTEGER NOT NULL,
            isProfileAutoSwitch INTEGER NOT NULL
        )
    """.trimIndent()

    private val MONITORED_APPS = """
        CREATE TABLE IF NOT EXISTS monitored_apps (
            profileId INTEGER NOT NULL,
            packageName TEXT NOT NULL,
            label TEXT NOT NULL,
            category TEXT NOT NULL,
            isWhitelistedEssential INTEGER NOT NULL,
            PRIMARY KEY(profileId, packageName)
        )
    """.trimIndent()

    private val APP_LIMITS = """
        CREATE TABLE IF NOT EXISTS app_limits (
            profileId INTEGER NOT NULL,
            packageName TEXT NOT NULL,
            dailyLimitMs INTEGER,
            weeklyLimitMs INTEGER,
            hourlyLimitMs INTEGER,
            sessionLimitMs INTEGER,
            breakDurationMs INTEGER,
            enabled INTEGER NOT NULL,
            frictionMethod TEXT,
            frictionDifficulty TEXT,
            extensionMsOnBypass INTEGER NOT NULL,
            PRIMARY KEY(profileId, packageName)
        )
    """.trimIndent()

    private val PAUSES = """
        CREATE TABLE IF NOT EXISTS pauses (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            profileId INTEGER,
            packageName TEXT,
            type TEXT NOT NULL,
            untilEpochMs INTEGER NOT NULL
        )
    """.trimIndent()

    private val USAGE_SESSIONS = """
        CREATE TABLE IF NOT EXISTS usage_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            packageName TEXT NOT NULL,
            profileId INTEGER NOT NULL,
            startEpochMs INTEGER NOT NULL,
            endEpochMs INTEGER NOT NULL,
            durationMs INTEGER NOT NULL
        )
    """.trimIndent()

    private val USAGE_AGGREGATES = """
        CREATE TABLE IF NOT EXISTS usage_aggregates (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            packageName TEXT NOT NULL,
            profileId INTEGER NOT NULL,
            period TEXT NOT NULL,
            periodStart INTEGER NOT NULL,
            totalMs INTEGER NOT NULL
        )
    """.trimIndent()

    private val OVERRIDE_EVENTS = """
        CREATE TABLE IF NOT EXISTS override_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            packageName TEXT NOT NULL,
            profileId INTEGER NOT NULL,
            timestamp INTEGER NOT NULL,
            method TEXT NOT NULL,
            extensionMs INTEGER NOT NULL
        )
    """.trimIndent()

    private val SESSION_STATE_V11 = """
        CREATE TABLE IF NOT EXISTS session_state (
            profileId INTEGER NOT NULL,
            packageName TEXT NOT NULL PRIMARY KEY,
            sessionStartEpochMs INTEGER NOT NULL,
            breakUntilEpochMs INTEGER,
            excludedMs INTEGER NOT NULL DEFAULT 0,
            frictionStartedAtEpochMs INTEGER,
            pendingWaitUntilEpochMs INTEGER,
            sessionLimitNotified INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent()

    private val SESSION_STATE_V12 = """
        CREATE TABLE IF NOT EXISTS session_state (
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
    """.trimIndent()

    private const val INDEX_SCHEDULE_SEGMENTS_PROFILE_ID =
        "CREATE INDEX IF NOT EXISTS index_schedule_segments_profileId ON schedule_segments(profileId)"
    private const val INDEX_SCHEDULE_WINDOWS_PROFILE_ID =
        "CREATE INDEX IF NOT EXISTS index_schedule_windows_profileId ON schedule_windows(profileId)"
    private const val INDEX_SCHEDULE_WINDOWS_SEGMENT_ID =
        "CREATE INDEX IF NOT EXISTS index_schedule_windows_segmentId ON schedule_windows(segmentId)"
    private const val INDEX_PAUSES_PROFILE_ID =
        "CREATE INDEX IF NOT EXISTS index_pauses_profileId ON pauses(profileId)"
    private const val INDEX_USAGE_SESSIONS_PROFILE_ID =
        "CREATE INDEX IF NOT EXISTS index_usage_sessions_profileId ON usage_sessions(profileId)"
    private const val INDEX_USAGE_SESSIONS_PACKAGE_NAME =
        "CREATE INDEX IF NOT EXISTS index_usage_sessions_packageName ON usage_sessions(packageName)"
    private const val INDEX_USAGE_AGGREGATES =
        "CREATE INDEX IF NOT EXISTS index_usage_aggregates_profileId_packageName_period_periodStart " +
            "ON usage_aggregates(profileId, packageName, period, periodStart)"
    private const val INDEX_OVERRIDE_EVENTS_PROFILE_ID =
        "CREATE INDEX IF NOT EXISTS index_override_events_profileId ON override_events(profileId)"
}
