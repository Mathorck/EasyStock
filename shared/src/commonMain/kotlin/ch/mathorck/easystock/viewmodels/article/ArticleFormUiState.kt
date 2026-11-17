package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.enums.article.ArticleFormMode
import ch.mathorck.easystock.enums.attributes.AttributeError

data class ArticleFormUiState(
    val mode: ArticleFormMode,
    /** The form as shown, with the template name applied ([withTemplateName]). */
    val form: ArticleForm,
    /** Active categories. */
    val categories: List<Category>,
    /** Name of the stored category, also when it is soft-deleted. Null on create. */
    val currentCategoryName: String? = null,
    val errors: ArticleFormErrors,
    val saving: Boolean = false,
    /** The last save failed for an unexpected reason. */
    val saveFailed: Boolean = false,
    /** Attributes of the chosen category, in order. */
    val attributes: List<CategoryAttributeWithDefinition> = emptyList(),
    val attributeErrors: Map<String, AttributeError> = emptyMap(),
    /** The name rendered from the category template, null when the category has none. */
    val templateName: String? = null,
    /** Create only: the NFC tag the new article gets (opened from an unknown scanned tag). */
    val tagId: String? = null,
) {
    val canSave: Boolean get() = errors.isValid && attributeErrors.isEmpty() && !saving

    /** The name is the rendered template. */
    val followsTemplate: Boolean get() = templateName != null && form.nameAuto
}
