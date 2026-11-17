package ch.mathorck.easystock.viewmodels.categories

import ch.mathorck.easystock.data.model.categories.Category

/** The chosen category id, and the category to create first (if any). */
internal data class CategoryChoice(val id: String?, val newCategory: Category?)
