package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.enums.article.FieldError

/** One error per field, null when the field is valid. */
data class ArticleFormErrors(
    val name: FieldError? = null,
    val sku: FieldError? = null,
    val unit: FieldError? = null,
    val category: FieldError? = null,
    val categoryName: FieldError? = null,
    val threshold: FieldError? = null,
    val orderUrl: FieldError? = null,
) {
    val isValid: Boolean get() = listOf(name, sku, unit, category, categoryName, threshold, orderUrl).all { it == null }
}
