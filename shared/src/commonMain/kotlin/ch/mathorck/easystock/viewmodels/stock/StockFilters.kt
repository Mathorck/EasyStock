package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.enums.article.Tracking
import ch.mathorck.easystock.enums.stock.StockLevelFilter
import ch.mathorck.easystock.enums.tags.TagFilter

/** Filters of the Stock page, combined with each other and with the search query (AND). */
data class StockFilters(
    /** Stock at this location and its descendants. Null = all locations. */
    val locationId: String? = null,
    val categoryId: String? = null,
    /** Consumables ([Tracking.QUANTITY]) or tools ([Tracking.SERIAL]). Null = both. */
    val tracking: Tracking? = null,
    val stockLevel: StockLevelFilter? = null,
    val tag: TagFilter? = null,
    /** Articles with a value for this attribute definition (article values only, not serial unit ones). */
    val attributeId: String? = null,
) {
    val isActive: Boolean get() = this != StockFilters()

    /** Low stock is never true for tools: choosing tools drops it. */
    fun withTracking(tracking: Tracking?): StockFilters = copy(
        tracking = tracking,
        stockLevel = stockLevel.takeUnless { tracking == Tracking.SERIAL && it == StockLevelFilter.LOW },
    )

    /** A level filter that is already set is cleared (toggle). */
    fun toggleStockLevel(level: StockLevelFilter): StockFilters =
        copy(stockLevel = level.takeUnless { it == stockLevel })
}
