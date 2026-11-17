package ch.mathorck.easystock.viewmodels.categories

import ch.mathorck.easystock.data.model.categories.Category

data class CategoryListUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val dialog: CategoryDialogState? = null,
)
