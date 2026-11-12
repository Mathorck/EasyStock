package ch.mathorck.easystock.backup

import ch.mathorck.easystock.sync.SyncTable
import ch.mathorck.easystock.sync.fromWire
import ch.mathorck.easystock.sync.toWire
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the merge test cases shared with the server (the JSON files of `docs/test-cases/merge`, format of the
 * server plan §1.4) against [planMerge]. Rows are wire rows (camelCase). As the plan says, `status`
 * and `actorRole` are ignored here; the `conflict` codes and the `final` rows (partial: only the
 * listed fields, but every row of a listed table) are asserted.
 */
class MergeTestCasesTest {
    private val dir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "docs/test-cases/merge") }
        .firstOrNull { it.isDirectory }
        ?: error("docs/test-cases/merge not found")

    @Test
    fun everyCasePasses() {
        val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        val failures = files.mapNotNull { file -> runCatching { run(file) }.exceptionOrNull()?.let { "${file.name}: ${it.message}" } }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    private fun run(file: File) {
        val case = Json.parseToJsonElement(file.readText()).jsonObject
        val existing = backupOf(case["existing"]?.jsonObject)
        val incoming = backupOf(case["incoming"]?.jsonObject)
        val plan = planMerge(existing, incoming)

        val expected = case.getValue("expected").jsonObject
        val expectedConflicts = expected["results"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .mapNotNull { r -> r["conflict"]?.jsonPrimitive?.content?.let { Triple(r.str("table"), r.str("id"), it) } }
            .sortedBy { it.toString() }
        val actualConflicts = plan.conflicts.map { Triple(it.table.tableName, it.rowId, it.type.name) }.sortedBy { it.toString() }
        assertEquals(expectedConflicts, actualConflicts, "conflicts")

        val result = wireOf(plan.result)
        expected["final"]?.jsonObject?.forEach { (table, rows) ->
            val actual = result[table].orEmpty().associateBy { it.str("id") }
            val wanted = rows.jsonArray.map { it.jsonObject }
            assertEquals(wanted.map { it.str("id") }.toSet(), actual.keys, "ids of $table")
            wanted.forEach { want ->
                val got = actual.getValue(want.str("id"))
                want.forEach { (field, value) ->
                    assertEquals(value, got[field] ?: JsonNull, "$table ${want.str("id")} $field")
                }
            }
        }
    }

    private fun JsonObject.str(field: String) = getValue(field).jsonPrimitive.content

    private fun rows(json: JsonObject?, table: SyncTable): List<JsonObject> =
        (json?.get(table.tableName) as? JsonArray).orEmpty().map { it.jsonObject }

    private fun <T> entities(json: JsonObject?, table: SyncTable): List<T> = rows(json, table).map { fromWire(table, it) }

    private fun backupOf(json: JsonObject?) = BackupData(
        categories = entities(json, SyncTable.CATEGORY),
        attributeDefinitions = entities(json, SyncTable.ATTRIBUTE_DEFINITION),
        categoryAttributes = entities(json, SyncTable.CATEGORY_ATTRIBUTE),
        locations = entities(json, SyncTable.LOCATION),
        articles = entities(json, SyncTable.ARTICLE),
        items = entities(json, SyncTable.ITEM),
        movements = entities(json, SyncTable.STOCK_MOVEMENT),
        reviews = entities(json, SyncTable.STOCK_REVIEW),
        reviewLines = entities(json, SyncTable.STOCK_REVIEW_LINE),
    )

    private fun wireOf(data: BackupData): Map<String, List<JsonObject>> = mapOf(
        "category" to data.categories.map { toWire(SyncTable.CATEGORY, it) },
        "attribute_definition" to data.attributeDefinitions.map { toWire(SyncTable.ATTRIBUTE_DEFINITION, it) },
        "category_attribute" to data.categoryAttributes.map { toWire(SyncTable.CATEGORY_ATTRIBUTE, it) },
        "location" to data.locations.map { toWire(SyncTable.LOCATION, it) },
        "article" to data.articles.map { toWire(SyncTable.ARTICLE, it) },
        "item" to data.items.map { toWire(SyncTable.ITEM, it) },
        "stock_movement" to data.movements.map { toWire(SyncTable.STOCK_MOVEMENT, it) },
        "stock_review" to data.reviews.map { toWire(SyncTable.STOCK_REVIEW, it) },
        "stock_review_line" to data.reviewLines.map { toWire(SyncTable.STOCK_REVIEW_LINE, it) },
    )

}
