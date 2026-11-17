package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.enums.attributes.CatalogueError

data class AttributeFormErrors(
    val label: CatalogueError? = null,
    val unit: CatalogueError? = null,
    val options: CatalogueError? = null,
) {
    val isValid: Boolean get() = label == null && unit == null && options == null
}
