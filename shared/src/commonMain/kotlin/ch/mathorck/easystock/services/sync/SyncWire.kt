package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.enums.sync.SyncTable
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

/**
 * An entity as a wire row. An article carries [imageSha256] instead of its local `image_path`;
 * a movement carries no author nor device and a flag no author (the server sets them from the token).
 */
@Suppress("UNCHECKED_CAST")
fun <T> toWire(table: SyncTable, row: T, imageSha256: String? = null): JsonObject {
    val json = RowJson.encodeToJsonElement(table.serializer as KSerializer<T>, row).jsonObject
    val local = when (table) {
        SyncTable.ARTICLE -> JsonObject(json - WireFields.IMAGE_PATH)
        SyncTable.STOCK_MOVEMENT -> JsonObject(json - WireFields.CREATED_BY - WireFields.DEVICE_ID)
        SyncTable.ARTICLE_FLAG -> JsonObject(json - WireFields.CREATED_BY)
        else -> json
    }
    val wire = local.renameKeys(::snakeToCamel)
    return if (table == SyncTable.ARTICLE) JsonObject(wire + (WireFields.IMAGE_SHA256 to JsonPrimitive(imageSha256))) else wire
}

/**
 * A wire row as an entity. An article's `imageSha256` becomes [imagePath] (chosen by the caller,
 * see `SyncStore`). Unknown fields (`serverSeq`, `baseSeq`, newer columns) are ignored: the seq is
 * stored apart (`server_seq` table, see [JsonObject.serverSeq]).
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

/** The `serverSeq` of a pulled wire row, null when absent or not a whole number. */
val JsonObject.serverSeq: Long? get() = (this[WireFields.SERVER_SEQ] as? JsonPrimitive)?.takeUnless { it.isString }?.contentOrNull?.toLongOrNull()

/** Whether the seq of [table] rows is kept and sent back: every synced table except the append-only movements. */
val SyncTable.hasServerSeq: Boolean get() = this != SyncTable.STOCK_MOVEMENT

/** [this] pushed as an edit of the server version [baseSeq]; unchanged when null (a row the server never had). */
fun JsonObject.withBaseSeq(baseSeq: Long?): JsonObject =
    if (baseSeq == null) this else JsonObject(this + (WireFields.BASE_SEQ to JsonPrimitive(baseSeq)))

/** [this] without its `baseSeq`: the row content only, as `SyncStore.wireRow` gives it. */
fun JsonObject.withoutBaseSeq(): JsonObject = if (WireFields.BASE_SEQ in this) JsonObject(this - WireFields.BASE_SEQ) else this

/** The `imageSha256` of a wire article row. */
val JsonObject.imageSha256: String? get() = (this[WireFields.IMAGE_SHA256] as? JsonPrimitive)?.contentOrNull

/** The value of a snake_case column in a wire row, null when absent or null. */
fun JsonObject.column(snakeName: String): String? =
    (this[snakeToCamel(snakeName)] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

/** [this] with the fields of [changes] on top: a "changed" answer may carry only the changed fields. */
fun JsonObject.overlay(changes: JsonObject?): JsonObject = if (changes.isNullOrEmpty()) this else JsonObject(this + changes)

/** A JSON value as text (for the "what changed" line of a rejected change). */
internal fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
