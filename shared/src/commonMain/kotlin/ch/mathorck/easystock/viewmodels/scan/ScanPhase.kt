package ch.mathorck.easystock.viewmodels.scan

/** Where the scan flow is. */
sealed interface ScanPhase {
    data object Ready : ScanPhase

    /** Looking the tag up. */
    data class Reading(val tagId: String) : ScanPhase

    /** No active article or location holds [tagId]: link it to one, or create one with it. */
    data class NotFound(val tagId: String) : ScanPhase

    /** The tag could not be read, or the lookup failed. */
    data object Error : ScanPhase

    /** The detail of [articleId] is shown. */
    data class Opened(val articleId: String) : ScanPhase

    /** The contents of [locationId] are shown. */
    data class OpenedLocation(val locationId: String) : ScanPhase
}
