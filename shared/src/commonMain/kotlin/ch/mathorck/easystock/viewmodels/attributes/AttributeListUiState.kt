package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition

data class AttributeListUiState(
    val loading: Boolean = true,
    val definitions: List<AttributeDefinition> = emptyList(),
    val dialog: AttributeDialogState? = null,
    /** One-shot: articles renamed by the last edit (their category name template uses it). */
    val renamedCount: Int? = null,
)
