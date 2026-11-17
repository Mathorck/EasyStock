package ch.mathorck.easystock.viewmodels.location

data class LocationListUiState(
    val loading: Boolean = true,
    val rows: List<LocationRow> = emptyList(),
    val dialog: LocationDialogState? = null,
    val pendingDelete: LocationDeleteRequest? = null,
    val message: LocationMessage? = null,
)
