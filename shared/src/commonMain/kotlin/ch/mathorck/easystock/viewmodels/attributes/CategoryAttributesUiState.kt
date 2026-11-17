package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.viewmodels.article.NameTemplateState

data class CategoryAttributesUiState(
    val loading: Boolean = true,
    /** Null once loaded means the category is deleted. */
    val categoryName: String? = null,
    /** Attributes of the category, in order (the draft when edited). */
    val rows: List<CategoryAttributeRow> = emptyList(),
    /** Active definitions not in [rows], by label. */
    val available: List<AttributeDefinition> = emptyList(),
    val template: NameTemplateState = NameTemplateState(),
    /** The draft differs from what is stored. */
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** One-shot: how many articles the last save renamed. Read it, then call [CategoryAttributesViewModel.consumeRenamed]. */
    val renamedCount: Int? = null,
) {
    val isDeleted: Boolean get() = !loading && categoryName == null
    val canSave: Boolean get() = dirty && !saving && template.error == null
}
