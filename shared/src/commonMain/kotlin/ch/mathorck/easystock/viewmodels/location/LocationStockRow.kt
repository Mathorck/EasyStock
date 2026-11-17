package ch.mathorck.easystock.viewmodels.location

// ---------- Detail ----------

/** Stock of the article at one location. [path] is e.g. "Workshop › Shelf A". */
data class LocationStockRow(
    val locationId: String,
    val path: String,
    val stockMilli: Long,
    val icon: String? = null,
    val color: String? = null,
)
