package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.db.CategoryNotFoundException
import ch.mathorck.easystock.data.db.DeletedCategoryNameException
import ch.mathorck.easystock.data.db.DuplicateCategoryNameException
import ch.mathorck.easystock.data.model.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Checks a category name. [categories]: active ones; [editingId]: the renamed one, null for a new one.
 * [rejectedName]/[rejectedReason]: name the database refused on the last save.
 */
fun validateCategoryName(
    name: String,
    categories: List<Category>,
    editingId: String?,
    rejectedName: String? = null,
    rejectedReason: CatalogueError? = null,
): CatalogueError? {
    val value = name.trim()
    return when {
        value.isEmpty() -> CatalogueError.REQUIRED
        value.length > MAX_CATEGORY_NAME_LENGTH -> CatalogueError.TOO_LONG
        categories.any { it.id != editingId && it.name.equals(value, ignoreCase = true) } -> CatalogueError.DUPLICATE
        rejectedName != null && rejectedName.equals(value, ignoreCase = true) -> rejectedReason
        else -> null
    }
}

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

data class CategoryListUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val dialog: CategoryDialogState? = null,
)

private data class CategoryDialogInput(
    val editing: Category?,
    val name: String,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val rejectedName: String? = null,
    val rejectedReason: CatalogueError? = null,
)

/** Settings › Categories: list, create, rename and soft delete. */
@OptIn(ExperimentalUuidApi::class)
class CategoryListViewModel(
    private val dao: CategoryDao,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    private val dialog = MutableStateFlow<CategoryDialogInput?>(null)

    val state: StateFlow<CategoryListUiState> = combine(dao.observeAll(), dialog) { categories, input ->
        CategoryListUiState(
            loading = false,
            categories = categories,
            dialog = input?.let {
                CategoryDialogState(
                    editing = it.editing,
                    name = it.name,
                    error = validateCategoryName(it.name, categories, it.editing?.id, it.rejectedName, it.rejectedReason),
                    saving = it.saving,
                    saveFailed = it.failed,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryListUiState())

    fun startCreate() {
        dialog.value = CategoryDialogInput(editing = null, name = "")
    }

    fun startRename(category: Category) {
        dialog.value = CategoryDialogInput(editing = category, name = category.name)
    }

    fun updateName(name: String) {
        dialog.update { it?.copy(name = name, failed = false) }
    }

    fun cancel() {
        dialog.value = null
    }

    fun save() {
        val input = dialog.value ?: return
        val current = state.value.dialog ?: return
        if (!current.canSave || current.name != input.name) return
        dialog.value = input.copy(saving = true, failed = false)
        viewModelScope.launch {
            val name = input.name.trim()
            try {
                val now = clock.now().toEpochMilliseconds()
                val editing = input.editing
                if (editing == null) {
                    dao.create(Category(id = newId(), name = name, createdAt = now, updatedAt = now))
                } else {
                    dao.rename(editing.id, name, now)
                }
                dialog.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateCategoryNameException) {
                dialog.update { it?.copy(saving = false, rejectedName = name, rejectedReason = CatalogueError.DUPLICATE) }
            } catch (e: DeletedCategoryNameException) {
                dialog.update { it?.copy(saving = false, rejectedName = name, rejectedReason = CatalogueError.DELETED_NAME) }
            } catch (e: CategoryNotFoundException) {
                dialog.value = null
            } catch (e: Exception) {
                dialog.update { it?.copy(saving = false, failed = true) }
            }
        }
    }

    /** Soft delete. Articles keep their category id; the detail screen still shows its name. */
    fun delete(category: Category) {
        if (dialog.value?.editing?.id == category.id) dialog.value = null
        viewModelScope.launch {
            try {
                dao.softDelete(category.id, clock.now().toEpochMilliseconds())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing deleted, the row stays in the list
            }
        }
    }
}
