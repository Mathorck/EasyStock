package ch.mathorck.easystock.viewmodels.scan

import ch.mathorck.easystock.enums.ScanState
import ch.mathorck.easystock.viewmodels.tags.TagMoveRequest
import ch.mathorck.easystock.viewmodels.tags.TagPickerUiState

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Ready,
    /** What the scan circle shows. */
    val scanState: ScanState = ScanState.Ready,
    val picker: TagPickerUiState? = null,
    val moveRequest: TagMoveRequest? = null,
) {
    /** The unknown tag, while [ScanState.NotFound]. */
    val unknownTagId: String? get() = (phase as? ScanPhase.NotFound)?.tagId

    /** The article whose detail is shown instead of the scan circle. */
    val openArticleId: String? get() = (phase as? ScanPhase.Opened)?.articleId

    /** The location whose contents are shown instead of the scan circle. */
    val openLocationId: String? get() = (phase as? ScanPhase.OpenedLocation)?.locationId

    /** The page takes tags: not while a lookup runs, a detail is open or a dialog is shown. */
    val acceptsTags: Boolean
        get() = phase !is ScanPhase.Reading && phase !is ScanPhase.Opened && phase !is ScanPhase.OpenedLocation &&
            picker == null && moveRequest == null
}
