package ch.mathorck.easystock.viewmodels.categories

import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.enums.attributes.CatalogueError

internal data class CategoryDialogInput(
    val editing: Category?,
    val name: String,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val rejectedName: String? = null,
    val rejectedReason: CatalogueError? = null,
)
