package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Synced rows on the wire (03-server-spec.md, "API conventions"): the entity JSON of the app (its
// snake_case @SerialName values), with camelCase top-level names. Nested JSON values (`attributes`,
// `pending_item`) are content, never renamed. Timestamps stay epoch milliseconds UTC.
// Every column is sent, nulls included: a missing key never means "unchanged".

/** JSON of the entities: every field written, nulls included (a cleared tag must reach the server). */
internal val RowJson: Json = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
}

/** `article_id` to `articleId`, `image_sha256` to `imageSha256`. */
fun snakeToCamel(name: String): String = buildString {
    var upper = false
    for (c in name) {
        when {
            c == '_' -> upper = true
            upper -> {
                append(c.uppercaseChar())
                upper = false
            }
            else -> append(c)
        }
    }
}

/** `articleId` to `article_id`, `imageSha256` to `image_sha256`. */
fun camelToSnake(name: String): String = buildString {
    for (c in name) {
        if (c.isUpperCase()) {
            append('_')
            append(c.lowercaseChar())
        } else {
            append(c)
        }
    }
}

private fun JsonObject.renameKeys(rename: (String) -> String): JsonObject = JsonObject(entries.associate { (k, v) -> rename(k) to v })

/** The 9 synced tables, in dependency order (parents first), with their entity and references. */
enum class SyncTable(val tableName: String, internal val serializer: KSerializer<*>, val references: Map<String, String> = emptyMap()) {
    CATEGORY("category", Category.serializer()),
    ATTRIBUTE_DEFINITION("attribute_definition", AttributeDefinition.serializer()),
    CATEGORY_ATTRIBUTE(
        "category_attribute",
        CategoryAttribute.serializer(),
        mapOf("category_id" to "category", "attribute_id" to "attribute_definition"),
    ),
    LOCATION("location", Location.serializer(), mapOf("parent_id" to "location")),
    ARTICLE("article", Article.serializer(), mapOf("category_id" to "category")),
    ITEM("item", Item.serializer(), mapOf("article_id" to "article", "location_id" to "location")),
    STOCK_MOVEMENT(
        "stock_movement",
        StockMovement.serializer(),
        mapOf("article_id" to "article", "item_id" to "item", "location_id" to "location"),
    ),
    STOCK_REVIEW("stock_review", StockReview.serializer(), mapOf("location_id" to "location")),
    STOCK_REVIEW_LINE(
        "stock_review_line",
        StockReviewLine.serializer(),
        mapOf(
            "review_id" to "stock_review",
            "article_id" to "article",
            "item_id" to "item",
            "location_id" to "location",
            "found_location_id" to "location",
        ),
    ),
    ;

    companion object {
        private val byName = entries.associateBy { it.tableName }

        fun of(tableName: String): SyncTable? = byName[tableName]
    }
}

/** Wire names the app sets or reads itself. */
object WireFields {
    const val ID = "id"
    const val IMAGE_SHA256 = "imageSha256"
    const val IMAGE_PATH = "image_path"
    const val CREATED_BY = "created_by"
    const val DEVICE_ID = "device_id"
}

/**
 * An entity as a wire row. An article carries [imageSha256] instead of its local `image_path`;
 * a movement carries no author nor device (the server sets them from the token).
 */
@Suppress("UNCHECKED_CAST")
fun <T> toWire(table: SyncTable, row: T, imageSha256: String? = null): JsonObject {
    val json = RowJson.encodeToJsonElement(table.serializer as KSerializer<T>, row).jsonObject
    val local = when (table) {
        SyncTable.ARTICLE -> JsonObject(json - WireFields.IMAGE_PATH)
        SyncTable.STOCK_MOVEMENT -> JsonObject(json - WireFields.CREATED_BY - WireFields.DEVICE_ID)
        else -> json
    }
    val wire = local.renameKeys(::snakeToCamel)
    return if (table == SyncTable.ARTICLE) JsonObject(wire + (WireFields.IMAGE_SHA256 to JsonPrimitive(imageSha256))) else wire
}

/**
 * A wire row as an entity. An article's `imageSha256` becomes [imagePath] (chosen by the caller,
 * see `SyncStore`). Unknown fields (`serverSeq`, newer columns) are ignored.
 */
@Suppress("UNCHECKED_CAST")
fun <T> fromWire(table: SyncTable, wire: JsonObject, imagePath: String? = null): T {
    val withoutImage = if (table == SyncTable.ARTICLE) JsonObject(wire - WireFields.IMAGE_SHA256) else wire
    val local = withoutImage.renameKeys(::camelToSnake).let {
        if (table == SyncTable.ARTICLE) JsonObject(it + (WireFields.IMAGE_PATH to JsonPrimitive(imagePath))) else it
    }
    return RowJson.decodeFromJsonElement(table.serializer as KSerializer<T>, local)
}

/** The `id` of a wire row. */
val JsonObject.rowId: String? get() = (this[WireFields.ID] as? JsonPrimitive)?.contentOrNull

/** The `imageSha256` of a wire article row. */
val JsonObject.imageSha256: String? get() = (this[WireFields.IMAGE_SHA256] as? JsonPrimitive)?.contentOrNull

/** The value of a snake_case column in a wire row, null when absent or null. */
fun JsonObject.column(snakeName: String): String? =
    (this[snakeToCamel(snakeName)] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

/** [this] with the fields of [changes] on top: a "changed" answer may carry only the changed fields. */
fun JsonObject.overlay(changes: JsonObject?): JsonObject = if (changes.isNullOrEmpty()) this else JsonObject(this + changes)

/** A JSON value as text (for the "what changed" line of a rejected change). */
internal fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
