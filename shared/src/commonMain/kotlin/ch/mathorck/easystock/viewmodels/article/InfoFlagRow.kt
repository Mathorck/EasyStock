package ch.mathorck.easystock.viewmodels.article

/**
 * An open "info to fix" flag of the article, from a finished review. [location] is the path of the line
 * location, [flaggedAt] the review finish time (local time zone), [serialNumber] set for a unit.
 */
data class InfoFlagRow(
    val lineId: String,
    val note: String?,
    val location: String,
    val serialNumber: String?,
    val flaggedAt: String,
)
