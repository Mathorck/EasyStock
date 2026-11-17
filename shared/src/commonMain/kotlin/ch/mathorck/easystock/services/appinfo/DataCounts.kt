package ch.mathorck.easystock.services.appinfo

/** Row counts of the active data (soft-deleted rows are excluded, stock movements have none). */
data class DataCounts(
    val articles: Int = 0,
    val locations: Int = 0,
    val items: Int = 0,
    val stockMovements: Int = 0,
    val nfcTags: Int = 0,
    val openReviews: Int = 0,
)
