package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.enums.sync.SyncError

/** State of the sync engine, for the UI. */
data class SyncStatus(
    val running: Boolean = false,
    /** Rows written so far by the running full pull (after joining or a promotion), null otherwise. */
    val fullPullRows: Int? = null,
    val error: SyncError? = null,
    /** The Problem Details `type` behind [error], when the server sent one (a precise message on screen). */
    val problemType: String? = null,
    /** A 426 paused the sync until the app is updated. */
    val paused: Boolean = false,
    /**
     * Photos the server refused (quota, too large, not a JPEG...): their rows were pushed without them,
     * and they are sent again at each cycle until accepted. Not an [error]: the sync itself went through.
     */
    val imagesNotSent: Int = 0,
    /** The Problem Details `type` of the last refused photo (`image-quota-exceeded`...), when the server sent one. */
    val imageProblemType: String? = null,
)
