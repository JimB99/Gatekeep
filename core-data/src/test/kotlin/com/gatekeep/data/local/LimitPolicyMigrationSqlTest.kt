package com.gatekeep.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LimitPolicyMigrationSqlTest {

    @Test
    fun `v18 migration copies shared difficulty and does not write a 5-minute grant`() {
        val source = migrationsSourceFile().readText()
        val start = source.indexOf("val MIGRATION_17_18")
        val end = source.indexOf("val MIGRATION_", start + 1).takeIf { it >= 0 } ?: source.length
        val migration = source.substring(start, end)

        assertTrue(migration.contains("sessionRefillOverrideMs INTEGER DEFAULT NULL"))
        assertTrue(migration.contains("periodContinueInheritSessionCap INTEGER NOT NULL DEFAULT 1"))
        assertTrue(migration.contains("periodContinueDurationMs INTEGER NOT NULL DEFAULT 900000"))
        assertTrue(migration.contains("SET periodContinueInheritSessionCap = 1"))
        assertFalse(migration.contains("sessionLimitMs IS NOT NULL AND sessionLimitMs > 0"))
        assertTrue(migration.contains("openFrictionDifficulty = defaultFrictionDifficulty"))
        assertTrue(migration.contains("periodFrictionDifficulty = defaultFrictionDifficulty"))
        assertTrue(migration.contains("sessionFrictionDifficulty = defaultFrictionDifficulty"))
        assertFalse(migration.contains("300000"))
        assertFalse(migration.contains("sessionRefillOverrideMs INTEGER NOT NULL DEFAULT 300000"))
    }

    @Test
    fun `v19 migration only adds friction-paid flag and does not write a 5-minute grant`() {
        val source = migrationsSourceFile().readText()
        val start = source.indexOf("val MIGRATION_18_19")
        val end = source.indexOf("val MIGRATION_", start + 1).takeIf { it >= 0 } ?: source.length
        val migration = source.substring(start, end)

        assertTrue(migration.contains("limitFrictionPaidThisSitting INTEGER NOT NULL DEFAULT 0"))
        assertFalse(migration.contains("300000"))
        assertFalse(migration.contains("DEFAULT 300000"))
    }

    private fun migrationsSourceFile(): File {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir: File? = File(workingDir)
        while (dir != null) {
            val candidate = File(dir, "src/main/kotlin/com/gatekeep/data/local/GatekeepMigrations.kt")
            if (candidate.isFile) return candidate
            val nested = File(dir, "core-data/src/main/kotlin/com/gatekeep/data/local/GatekeepMigrations.kt")
            if (nested.isFile) return nested
            dir = dir.parentFile
        }
        error("Could not locate GatekeepMigrations.kt from $workingDir")
    }
}
