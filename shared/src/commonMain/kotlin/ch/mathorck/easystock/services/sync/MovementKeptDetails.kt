package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.enums.sync.KeepMineBlock

/** A `movement-kept` change as the screen shows it. */
data class MovementKeptDetails(
    /** The phone's version, never accepted by the server. */
    val phone: StockMovement,
    /** The server's version, now the local movement. Null when it is gone (a reset). */
    val server: StockMovement?,
    /** Why "Keep mine" is not offered, null when it is. */
    val keepMineBlock: KeepMineBlock?,
)
