package ch.mathorck.easystock.data.db

import ch.mathorck.easystock.backup.BackupManifest
import ch.mathorck.easystock.backup.BackupTable
import ch.mathorck.easystock.server.ClientVersions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DATA_SCHEMA_VERSION] (sent to the server, written in backups) must describe the synced tables of
 * [DB_VERSION]: a version that changes a synced column without raising it fails here.
 */
class DataSchemaVersionTest {
    private val dir = File("schemas/ch.mathorck.easystock.data.db.AppDatabase")

    private fun syncedTables(version: Int): Map<String, JsonElement> {
        val file = File(dir, "$version.json")
        assertTrue(file.isFile, "missing exported schema ${file.absolutePath}")
        val entities = Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        val synced = BackupTable.entries.map { it.tableName }.toSet()
        // Columns, primary key and foreign keys; indices and app-only tables do not change the data
        return entities.map { it.jsonObject }
            .filter { it["tableName"]!!.jsonPrimitive.content in synced }
            .associate { e -> e["tableName"]!!.jsonPrimitive.content to JsonObject(e.filterKeys { it in setOf("fields", "primaryKey", "foreignKeys") }) }
    }

    @Test
    fun syncedTablesOfDataVersionMatchTheCurrentVersion() {
        assertTrue(DATA_SCHEMA_VERSION <= DB_VERSION)
        val data = syncedTables(DATA_SCHEMA_VERSION)
        assertEquals(BackupTable.entries.size, data.size)
        assertEquals(data, syncedTables(DB_VERSION), "a synced table changed: set DATA_SCHEMA_VERSION to DB_VERSION (and add it on the server)")
    }

    @Test
    fun serverHeaderAndBackupsUseTheDataVersion() {
        assertEquals(DATA_SCHEMA_VERSION, ClientVersions(appVersion = "1.0.0").schemaVersion)
        assertEquals(DATA_SCHEMA_VERSION, BackupManifest(appVersion = "1.0.0", createdAt = "2026-01-01T00:00:00Z").schemaVersion)
        // v12 adds indices only: servers of schema [10, 11] keep syncing
        assertEquals(11, DATA_SCHEMA_VERSION)
    }
}
