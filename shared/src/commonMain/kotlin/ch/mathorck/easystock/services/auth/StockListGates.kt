package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.enums.auth.Action

/** Buttons of the stock lists (Stock page, location contents). */
data class StockListGates(
    /** "New article" button of the Stock page. */
    val createArticle: Boolean,
    /** Add, remove and move buttons on the rows of a location. */
    val stockActions: Boolean,
) {
    companion object {
        fun of(permissions: AppPermissions) = StockListGates(
            createArticle = permissions.can(Action.EDIT),
            stockActions = permissions.can(Action.STOCK_ACTION),
        )
    }
}
