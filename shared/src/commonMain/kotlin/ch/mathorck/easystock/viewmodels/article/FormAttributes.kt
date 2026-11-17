package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition

/** Attributes of the category chosen in the form, with the category id they belong to. */
data class FormAttributes(val categoryId: String?, val attributes: List<CategoryAttributeWithDefinition>)
