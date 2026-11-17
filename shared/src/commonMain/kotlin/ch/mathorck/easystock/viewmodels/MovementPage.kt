package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.dao.stock.MovementWithLocation

/**
 * The newest [limit] movements, read with one extra row ([limit] + 1) to know whether there are more.
 */
data class MovementPage(val movements: List<MovementWithLocation>, val hasMore: Boolean) {
    companion object {
        fun of(rows: List<MovementWithLocation>, limit: Int): MovementPage =
            MovementPage(rows.take(limit), rows.size > limit)
    }
}
