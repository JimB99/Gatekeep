package com.gatekeep.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the migration chain against real SQLite and validates the end state against the
 * exported v13 Room schema.
 *
 * Legacy databases are built by [LegacySchemaFixtures] because schema export only started at v13.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GatekeepDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrate12To13_addsOverrideEventIndicesAndPreservesRows() {
        LegacySchemaFixtures.createVersion12(context, TEST_DB)
        LegacySchemaFixtures.insertOverrideEvent(
            context = context,
            dbName = TEST_DB,
            packageName = "com.example",
            profileId = 1,
            timestamp = 1_000,
            method = "extension",
            extensionMs = 300_000,
        )

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            13,
            true,
            GatekeepMigrations.MIGRATION_12_13,
        )

        assertEquals(
            listOf(
                "index_override_events_profileId",
                "index_override_events_profileId_method_timestamp",
                "index_override_events_profileId_packageName_method_timestamp",
            ),
            db.indexNamesOf("override_events"),
        )
        assertEquals(1, db.countOf("override_events"))
        assertEquals("extension", db.singleTextOf("SELECT method FROM override_events"))
        db.close()
    }

    @Test
    fun migrate11To13_movesSessionStateToCompositeKeyAndKeepsOverrides() {
        LegacySchemaFixtures.createVersion11(context, TEST_DB)
        LegacySchemaFixtures.insertSessionStateV11(
            context = context,
            dbName = TEST_DB,
            profileId = 2,
            packageName = "com.example",
            sessionStartEpochMs = 5_000,
            excludedMs = 1_500,
        )
        LegacySchemaFixtures.insertOverrideEvent(
            context = context,
            dbName = TEST_DB,
            packageName = "com.example",
            profileId = 2,
            timestamp = 2_000,
            method = "noLimitToday",
            extensionMs = 0,
        )

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            13,
            true,
            GatekeepMigrations.MIGRATION_11_12,
            GatekeepMigrations.MIGRATION_12_13,
        )

        db.query(
            """
            SELECT profileId, packageName, sessionStartEpochMs, excludedMs, consecutiveExtensionCount
            FROM session_state
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(2L, cursor.getLong(0))
            assertEquals("com.example", cursor.getString(1))
            assertEquals(5_000L, cursor.getLong(2))
            assertEquals(1_500L, cursor.getLong(3))
            assertEquals(0, cursor.getInt(4))
        }
        assertEquals("noLimitToday", db.singleTextOf("SELECT method FROM override_events"))
        db.close()
    }

    @Test
    fun migrate8To13_fullChainProducesValidSchema() {
        LegacySchemaFixtures.createVersion8(context, TEST_DB)
        LegacySchemaFixtures.insertProfileV8(context, TEST_DB, name = "Work")
        LegacySchemaFixtures.insertScheduleWindowV8(
            context = context,
            dbName = TEST_DB,
            profileId = 1,
            dayOfWeek = 1,
            startMinute = 9 * 60,
            endMinute = 17 * 60,
        )

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, *GatekeepMigrations.ALL)

        assertEquals("Work", db.singleTextOf("SELECT name FROM profiles"))
        assertEquals("perApp", db.singleTextOf("SELECT limitUsageScope FROM profiles"))
        // 8 -> 9 converts enforcement windows into a segment and switches the fallback to block.
        assertEquals("block", db.singleTextOf("SELECT noScheduleMatchMode FROM profiles"))
        assertEquals(1, db.countOf("schedule_segments"))
        assertTrue(
            "Schedule window should be linked to the generated segment",
            db.countOf("schedule_windows WHERE segmentId IS NOT NULL") == 1,
        )
        db.close()
    }

    private fun SupportSQLiteDatabase.indexNamesOf(table: String): List<String> = query(
        """
        SELECT name FROM sqlite_master
        WHERE type = 'index' AND tbl_name = ? AND name NOT LIKE 'sqlite_%'
        ORDER BY name
        """.trimIndent(),
        arrayOf<Any>(table),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun SupportSQLiteDatabase.countOf(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.singleTextOf(sql: String): String =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private companion object {
        const val TEST_DB = "gatekeep-migration-chain-test"
    }
}
