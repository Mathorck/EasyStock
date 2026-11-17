package ch.mathorck.easystock.data.dao.stock

import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.stock.MovementReason

/**
 * What a [UnitAction] does from a status. [delta] is the movement on the unit (+1000, -1000 or 0 for none).
 * [needsLocation]: the unit comes back into stock, the user picks where.
 */
data class UnitTransition(val newStatus: ItemStatus, val delta: Long, val reason: MovementReason?, val needsLocation: Boolean)
