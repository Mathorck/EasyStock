package ch.mathorck.easystock.services.attributes

import ch.mathorck.easystock.enums.attributes.NameTemplateFilter

sealed interface NameTemplatePart {
    data class Literal(val text: String) : NameTemplatePart
    /** [unit]: symbol of the unit filter ("mm"), null when none. */
    data class Placeholder(
        val key: String,
        val filters: List<NameTemplateFilter> = emptyList(),
        val unit: String? = null,
    ) : NameTemplatePart
}
