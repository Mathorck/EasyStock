package ch.mathorck.easystock.viewmodels.categories

import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.enums.attributes.CatalogueError

/** The open category dialog. [editing] is null for a new category. */
data class CategoryDialogState(
    val editing: Category?,
    val name: String,
    val error: CatalogueError?,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = error == null && !saving
}
