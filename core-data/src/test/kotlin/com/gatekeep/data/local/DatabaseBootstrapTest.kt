package com.gatekeep.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBootstrapTest {

    @Test
    fun `isMigrationFailure detects room migration errors`() {
        val error = IllegalStateException("Migration didn't properly handle: override_events")
        assertTrue(DatabaseBootstrap.isMigrationFailure(error))
    }

    @Test
    fun `isMigrationFailure ignores unrelated errors`() {
        val error = IllegalArgumentException("invalid profile name")
        assertFalse(DatabaseBootstrap.isMigrationFailure(error))
    }

    @Test
    fun `isMigrationFailure walks the cause chain`() {
        val nested = RuntimeException(
            "wrapper",
            IllegalStateException("Migration didn't properly handle: override_events"),
        )
        assertTrue(DatabaseBootstrap.isMigrationFailure(nested))
    }

    @Test
    fun `isSqliteBusy detects journal lock on open`() {
        val busy = IllegalStateException(
            "database is locked (code 5 SQLITE_BUSY): , while compiling: PRAGMA journal_mode",
        )
        assertTrue(DatabaseBootstrap.isSqliteBusy(busy))
        assertFalse(DatabaseBootstrap.isSqliteBusy(IllegalArgumentException("invalid profile name")))
    }
}
