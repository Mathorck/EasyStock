package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition

internal data class AttributeDialogInput(
    val editing: AttributeDefinition?,
    val form: AttributeForm,
    val saving: Boolean = false,
    val failed: Boolean = false,
)
