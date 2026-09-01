package com.gatekeep.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration11To12Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GatekeepDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate11To12_preservesSessionStateWithCompositeKey() {
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                """
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
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_state (
                    profileId, packageName, sessionStartEpochMs, excludedMs, sessionLimitNotified
                ) VALUES (2, 'com.example', 1000, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, GatekeepMigrations.MIGRATION_11_12)
        db.query("SELECT profileId, packageName, consecutiveExtensionCount FROM session_state").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(2L, cursor.getLong(0))
            assertEquals("com.example", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-11-12-test"
    }
}
