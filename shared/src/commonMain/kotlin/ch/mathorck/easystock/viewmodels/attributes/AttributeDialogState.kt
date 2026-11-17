package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition

/** The open attribute dialog. [editing] is null for a new attribute. */
data class AttributeDialogState(
    val editing: AttributeDefinition?,
    val form: AttributeForm,
    val errors: AttributeFormErrors,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = errors.isValid && !saving
}
