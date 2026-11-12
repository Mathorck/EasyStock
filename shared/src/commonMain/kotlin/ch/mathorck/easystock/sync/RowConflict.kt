package ch.mathorck.easystock.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// A stale edit (03-server-spec.md "Push processing", first change wins): another phone changed the row
// after this phone last saw it, the server rejected the edit with `conflict`, its row and its `serverSeq`.
// The phone writes the server's row (keep theirs, the default) and keeps its own version in the
// `rejected_change` row until the user chooses. "Keep mine" writes the phone's version again as a local
// change, pushed with `baseSeq` = the server's seq of that answer: it wins unless the row changed again
// meanwhile (then it conflicts again). No schema change: the payload is stored in `rejected_change.reason`.

object RowConflict {
    /** `rejected_change.reason` code, as the server sends it. Followed by `:` and the payload when the choice is offered. */
    const val REASON = CONFLICT_REASON

    private const val PREFIX = "$REASON:"
    private const val SEQ = "seq"
    private const val ROW = "row"

    /** The `reason` value that holds the phone's row [phoneRow] (a wire row) and the server's [seq]. */
    fun reasonOf(phoneRow: JsonObject, seq: Long): String =
        PREFIX + buildJsonObject {
            put(SEQ, seq)
            put(ROW, phoneRow.withoutBaseSeq())
        }.toString()

    /** The phone's row and the server's seq of a `conflict:` reason, null for any other reason or an unreadable one. */
    fun payloadOf(reason: String): Payload? {
        if (!reason.startsWith(PREFIX)) return null
        return try {
            val json = Json.parseToJsonElement(reason.substring(PREFIX.length)).jsonObject
            val seq = (json[SEQ] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return null
            val row = json[ROW] as? JsonObject ?: return null
            Payload(row, seq)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The reason code without its payload, for the screen. */
    fun codeOf(reason: String): String = if (reason.startsWith(PREFIX)) REASON else reason

    /**
     * The row "Keep mine" writes: the phone's version, changed [now] (a new local edit). The server
     * ignores `updatedAt` for conflicts; it only orders the local lists.
     */
    fun keepMineRow(phone: JsonObject, now: Long): JsonObject =
        if (UPDATED_AT in phone) JsonObject(phone + (UPDATED_AT to JsonPrimitive(now))) else phone

    private const val UPDATED_AT = "updatedAt"

    data class Payload(val phone: JsonObject, val seq: Long)
}

/** Why "Keep mine" is not offered for a row conflict. */
enum class RowKeepMineBlock {
    /** The role can't send changes any more. */
    ROLE,

    /** The row is no longer on this phone. */
    ROW_GONE,

    /** Another row now holds a unique value of the phone's version (name, SKU, NFC tag, serial number). */
    VALUE_TAKEN,

    /** The phone's version can't be written (unreadable, or a missing parent). */
    INVALID,
}
