package com.gatekeep.data.local

import android.content.Context
import androidx.room.Room

object DatabaseBootstrap {
    private const val DB_NAME = "gatekeep.db"

    fun open(
        context: Context,
        onMigrationFailure: (Throwable) -> Unit = {},
    ): GatekeepDatabase {
        return try {
            openAndValidate(buildDatabase(context))
        } catch (first: Exception) {
            if (!isMigrationFailure(first)) throw first
            onMigrationFailure(first)
            deleteDatabaseFiles(context)
            openAndValidate(buildDatabase(context))
        }
    }

    /**
     * Room does not run migrations until the first real open. Force-open so the caller
     * can catch schema mismatches instead of crashing later on the first query.
     */
    private fun openAndValidate(db: GatekeepDatabase): GatekeepDatabase {
        try {
            db.openHelper.writableDatabase
        } catch (error: Exception) {
            runCatching { db.close() }
            throw error
        }
        return db
    }

    private fun buildDatabase(context: Context): GatekeepDatabase =
        Room.databaseBuilder(context, GatekeepDatabase::class.java, DB_NAME)
            .addMigrations(*GatekeepMigrations.ALL)
            .build()

    fun isMigrationFailure(error: Throwable): Boolean {
        val message = generateSequence(error as Throwable?) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
        return MIGRATION_FAILURE_MARKERS.any { marker ->
            message.contains(marker, ignoreCase = true)
        }
    }

    fun deleteDatabaseFiles(context: Context) {
        val dbPath = context.getDatabasePath(DB_NAME)
        dbPath.parentFile?.mkdirs()
        context.deleteDatabase(DB_NAME)
        dbPath.parentFile?.listFiles()?.orEmpty()
            ?.filter { file ->
                file.name.startsWith("$DB_NAME-") || file.name == "$DB_NAME-journal"
            }
            ?.forEach { it.delete() }
    }

    private val MIGRATION_FAILURE_MARKERS = listOf(
        "Migration didn't properly handle",
        "Room cannot verify the data integrity",
        "A migration from",
        "Failed to migrate",
        "Pre-packaged database has an invalid schema",
        "no such table",
        "no such column",
        "duplicate column name",
    )
}
