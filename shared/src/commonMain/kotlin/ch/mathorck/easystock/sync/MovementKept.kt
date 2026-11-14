package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.db.STOCK_IN_REASONS
import ch.mathorck.easystock.data.db.STOCK_OUT_REASONS
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

// `MOVEMENT_KEPT` (02-app.md, "Open points (sync engine)"): the server already had a movement with
// this id and kept its own. The phone shows the server's movement (the stock matches the server) and
// keeps its own version in the `rejected_change` row until the user chooses: use the server's
// version, or keep mine (sent again as a new movement). No schema change: the phone's wire row is
// stored in `rejected_change.reason`, after the code.
//
// Since movements are append-only on the phone too, a phone never writes the server's version over its
// own any more: a `movement-kept` entry is only left by an older app version (its local movement then
// is the server's). The current answer is a `movement-differs` entry ([MovementDiffers]).

object MovementKept {
    /** `rejected_change.reason` code, followed by `:` and the phone's wire row. */
    const val REASON = "movement-kept"

    private const val PREFIX = "$REASON:"

    /** The `reason` value that holds the phone's movement [phoneRow] (a wire row). */
    fun reasonOf(phoneRow: JsonObject): String = PREFIX + phoneRow.toString()

    /** The phone's wire row of a `movement-kept` reason, null for any other reason or an unreadable one. */
    fun phoneRowOf(reason: String): JsonObject? {
        if (!reason.startsWith(PREFIX)) return null
        return try {
            Json.parseToJsonElement(reason.substring(PREFIX.length)).jsonObject
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The reason code without its payload, for the screen. */
    fun codeOf(reason: String): String = if (reason.startsWith(PREFIX)) REASON else reason

    /** The phone's movement of a `movement-kept` reason, null when there is none or it can't be read. */
    fun phoneMovementOf(reason: String): StockMovement? = phoneRowOf(reason)?.let {
        try {
            fromWire<StockMovement>(SyncTable.STOCK_MOVEMENT, it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/**
 * The server has a movement with this id and other values (`MOVEMENT_KEPT`, or a changed row sent back).
 * Movements are append-only: the phone keeps its own and only reports it, with both versions in
 * `rejected_change.reason` (`movement-differs:{"phone":…,"server":…}`). Its stock may then differ from
 * the server's for this movement. Nothing to choose: "Keep mine" would count the phone's quantity twice.
 */
object MovementDiffers {
    /** `rejected_change.reason` code, followed by `:` and both wire rows. */
    const val REASON = "movement-differs"

    private const val PREFIX = "$REASON:"
    private const val PHONE = "phone"
    private const val SERVER = "server"

    /** The `reason` value that holds the phone's movement [phoneRow] and the server's [serverRow] (wire rows). */
    fun reasonOf(phoneRow: JsonObject, serverRow: JsonObject): String =
        PREFIX + buildJsonObject {
            put(PHONE, phoneRow.withoutBaseSeq())
            put(SERVER, serverRow.withoutBaseSeq())
        }.toString()

    /** Both movements of a `movement-differs` reason, null for any other reason or an unreadable one. */
    fun versionsOf(reason: String): Versions? {
        if (!reason.startsWith(PREFIX)) return null
        return try {
            val json = Json.parseToJsonElement(reason.substring(PREFIX.length)).jsonObject
            val phone = json[PHONE] as? JsonObject ?: return null
            val server = json[SERVER] as? JsonObject ?: return null
            Versions(fromWire(SyncTable.STOCK_MOVEMENT, phone), fromWire(SyncTable.STOCK_MOVEMENT, server))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    data class Versions(val phone: StockMovement, val server: StockMovement)
}

/**
 * True when [a] and [b] are the same movement apart from their author fields (`created_by`, `device_id`),
 * the only columns sync may update on a movement the phone already has.
 */
fun sameMovementContent(a: StockMovement, b: StockMovement): Boolean =
    a.copy(createdBy = null, deviceId = null) == b.copy(createdBy = null, deviceId = null)

/** Why "Keep mine" is not offered. */
enum class KeepMineBlock {
    /** The role can't add stock movements (Viewer, Guest). */
    ROLE,

    /** A serial unit's movement: sent again, the unit could be in two places at once. */
    SERIAL_UNIT,

    /** One half of a transfer: sent again alone, the stock would not add up. */
    TRANSFER,

    /** The article is deleted. */
    ARTICLE_GONE,

    /** An entry to a deleted location. */
    LOCATION_GONE,

    /** A removal larger than the stock at the location now. */
    NOT_ENOUGH_STOCK,

    /** A reason that doesn't match the direction (never made by this app). */
    INVALID,
}

/**
 * Null when [phone] can be sent again as a new movement, otherwise why not. Same rules as
 * `StockActionDao.adjust`: [articleActive] and [locationActive] are read now, [stockAtLocation] is
 * the current stock of the article at the movement's location (the server's movement included).
 */
fun keepMineBlock(
    phone: StockMovement,
    canStockAction: Boolean,
    articleActive: Boolean,
    locationActive: Boolean,
    stockAtLocation: Long,
): KeepMineBlock? = when {
    !canStockAction -> KeepMineBlock.ROLE
    phone.itemId != null -> KeepMineBlock.SERIAL_UNIT
    phone.reason == MovementReason.TRANSFER -> KeepMineBlock.TRANSFER
    phone.quantityMilli == 0L -> KeepMineBlock.INVALID
    phone.quantityMilli > 0 && phone.reason !in STOCK_IN_REASONS -> KeepMineBlock.INVALID
    phone.quantityMilli < 0 && phone.reason !in STOCK_OUT_REASONS -> KeepMineBlock.INVALID
    !articleActive -> KeepMineBlock.ARTICLE_GONE
    phone.quantityMilli > 0 && !locationActive -> KeepMineBlock.LOCATION_GONE
    phone.quantityMilli < 0 && stockAtLocation + phone.quantityMilli < 0 -> KeepMineBlock.NOT_ENOUGH_STOCK
    else -> null
}

/**
 * The new movement of "Keep mine": the phone's quantity, article, unit, location, reason and comment,
 * with a new id and [now] as both times. The author and device are set by the server.
 */
fun keepMineMovement(phone: StockMovement, newId: String, now: Long): StockMovement =
    phone.copy(id = newId, occurredAt = now, createdAt = now, createdBy = null, deviceId = null)

/** A `movement-kept` change as the screen shows it. */
data class MovementKeptDetails(
    /** The phone's version, never accepted by the server. */
    val phone: StockMovement,
    /** The server's version, now the local movement. Null when it is gone (a reset). */
    val server: StockMovement?,
    /** Why "Keep mine" is not offered, null when it is. */
    val keepMineBlock: KeepMineBlock?,
)
