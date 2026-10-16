package com.gatekeep.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

object DatabaseBootstrap {
    private const val DB_NAME = "gatekeep.db"

    fun open(
        context: Context,
        onMigrationFailure: (Throwable) -> Unit = {},
    ): GatekeepDatabase {
        var lastBusy: Exception? = null
        repeat(6) { attempt ->
            try {
                return try {
                    openAndValidate(buildDatabase(context))
                } catch (first: Exception) {
                    if (!isMigrationFailure(first)) throw first
                    onMigrationFailure(first)
                    deleteDatabaseFiles(context)
                    openAndValidate(buildDatabase(context))
                }
            } catch (error: Exception) {
                if (!isSqliteBusy(error)) throw error
                lastBusy = error
                Thread.sleep(40L * (attempt + 1))
            }
        }
        throw lastBusy ?: IllegalStateException("Room open retry exhausted")
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
            // WAL extra reader connections race setLocaleFromConfiguration on API 36 (SQLITE_BUSY).
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    internal fun isSqliteBusy(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { thrown ->
            val message = thrown.message.orEmpty()
            message.contains("SQLITE_BUSY", ignoreCase = true) ||
                message.contains("database is locked", ignoreCase = true) ||
                thrown.javaClass.simpleName.contains("DatabaseLocked")
        }

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
