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
}
