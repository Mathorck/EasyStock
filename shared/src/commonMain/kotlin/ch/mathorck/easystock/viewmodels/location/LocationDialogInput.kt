package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location

internal data class LocationDialogInput(
    val editing: Location?,
    val form: LocationForm,
    val tagId: String? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
)
