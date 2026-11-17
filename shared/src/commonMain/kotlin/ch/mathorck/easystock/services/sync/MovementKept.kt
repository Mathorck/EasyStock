package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dao.stock.STOCK_IN_REASONS
import ch.mathorck.easystock.data.dao.stock.STOCK_OUT_REASONS
import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.enums.stock.MovementReason
import ch.mathorck.easystock.enums.sync.KeepMineBlock
import ch.mathorck.easystock.enums.sync.SyncTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * True when [a] and [b] are the same movement apart from their author fields (`created_by`, `device_id`),
 * the only columns sync may update on a movement the phone already has.
 */
fun sameMovementContent(a: StockMovement, b: StockMovement): Boolean =
    a.copy(createdBy = null, deviceId = null) == b.copy(createdBy = null, deviceId = null)

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
