package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.enums.sync.SyncTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

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
