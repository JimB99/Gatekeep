package com.gatekeep.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against the class of failure that produced
 * "Migration didn't properly handle: override_events": a migration creating an index whose name
 * does not match the name Room derives from the `@Entity` annotation.
 *
 * Runs on the JVM so it catches naming drift without needing a device.
 */
class MigrationSchemaConsistencyTest {

    private val schemaIndexNames: Set<String> by lazy {
        val root = Json.parseToJsonElement(latestSchemaFile().readText()).jsonObject
        val entities = root.getValue("database").jsonObject.getValue("entities").jsonArray
        entities.flatMap { entity ->
            entity.jsonObject["indices"]?.jsonArray.orEmpty().map { index ->
                index.jsonObject.getValue("name").jsonPrimitive.content
            }
        }.toSet()
    }

    @Test
    fun `every index created by a migration exists in the exported schema`() {
        val migrationSource = migrationsSourceFile().readText()
        val createdIndexNames = INDEX_NAME_REGEX.findAll(migrationSource)
            .map { it.value }
            .toSet()

        assertTrue(
            "Expected migrations to create at least one index",
            createdIndexNames.isNotEmpty(),
        )

        val unknown = createdIndexNames - schemaIndexNames
        assertEquals(
            "Migrations create indices that Room does not expect. " +
                "Rename them to the Room-generated names in core-data/schemas, " +
                "or declare them on the @Entity.",
            emptySet<String>(),
            unknown,
        )
    }

    @Test
    fun `override_events carries the composite indices used by quota queries`() {
        listOf(
            "index_override_events_profileId",
            "index_override_events_profileId_packageName_method_timestamp",
            "index_override_events_profileId_method_timestamp",
        ).forEach { expected ->
            assertTrue(
                "Missing expected index $expected in exported schema",
                expected in schemaIndexNames,
            )
        }
    }

    @Test
    fun `exported schema version matches the database version constant`() {
        val databaseSource = File(moduleRoot(), DATABASE_SOURCE_PATH).readText()
        val declaredVersion = VERSION_REGEX.find(databaseSource)
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: error("Could not read version from GatekeepDatabase.kt")

        assertEquals(
            "Exported schema JSON is stale. Re-run :core-data:kspDebugKotlin and commit the schema.",
            declaredVersion,
            latestSchemaFile().nameWithoutExtension.toInt(),
        )
    }

    private fun latestSchemaFile(): File {
        val schemaDir = File(moduleRoot(), SCHEMA_DIR)
        require(schemaDir.isDirectory) { "Missing exported Room schemas at $schemaDir" }
        return schemaDir.listFiles { file -> file.extension == "json" }
            ?.maxByOrNull { it.nameWithoutExtension.toInt() }
            ?: error("No exported Room schema JSON found in $schemaDir")
    }

    private fun migrationsSourceFile(): File = File(moduleRoot(), MIGRATIONS_SOURCE_PATH)

    private fun moduleRoot(): File {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir: File? = File(workingDir)
        while (dir != null) {
            if (File(dir, SCHEMA_DIR).isDirectory) return dir
            if (File(dir, "core-data/$SCHEMA_DIR").isDirectory) return File(dir, "core-data")
            dir = dir.parentFile
        }
        error("Could not locate core-data module root from $workingDir")
    }

    private companion object {
        const val SCHEMA_DIR = "schemas/com.gatekeep.data.local.GatekeepDatabase"
        const val MIGRATIONS_SOURCE_PATH =
            "src/main/kotlin/com/gatekeep/data/local/GatekeepMigrations.kt"
        const val DATABASE_SOURCE_PATH =
            "src/main/kotlin/com/gatekeep/data/local/GatekeepDatabase.kt"
        val INDEX_NAME_REGEX = Regex("""index_[A-Za-z0-9_]+""")
        val VERSION_REGEX = Regex("""version\s*=\s*(\d+)""")
    }
}
