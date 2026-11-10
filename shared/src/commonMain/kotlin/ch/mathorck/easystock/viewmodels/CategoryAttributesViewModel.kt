package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.AttributeNotFoundException
import ch.mathorck.easystock.data.db.CategoryAttributeInput
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.model.AttributeDefinition
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

// ---------- Draft edits (pure) ----------

/** Appends [attributeId] (not required) unless it is already in the list. */
fun List<CategoryAttributeInput>.withAttribute(attributeId: String): List<CategoryAttributeInput> =
    if (any { it.attributeId == attributeId }) this else this + CategoryAttributeInput(attributeId, required = false)

fun List<CategoryAttributeInput>.withoutAttribute(attributeId: String): List<CategoryAttributeInput> =
    filterNot { it.attributeId == attributeId }

fun List<CategoryAttributeInput>.withRequired(attributeId: String, required: Boolean): List<CategoryAttributeInput> =
    map { if (it.attributeId == attributeId) it.copy(required = required) else it }

/** Moves [attributeId] by [delta] places (-1 = up). Stays within the list. */
fun List<CategoryAttributeInput>.moved(attributeId: String, delta: Int): List<CategoryAttributeInput> {
    val from = indexOfFirst { it.attributeId == attributeId }
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

// ---------- State ----------

data class CategoryAttributeRow(val definition: AttributeDefinition, val required: Boolean)

data class CategoryAttributesUiState(
    val loading: Boolean = true,
    /** Null once loaded means the category is deleted. */
    val categoryName: String? = null,
    /** Attributes of the category, in order (the draft when edited). */
    val rows: List<CategoryAttributeRow> = emptyList(),
    /** Active definitions not in [rows], by label. */
    val available: List<AttributeDefinition> = emptyList(),
    /** The draft differs from what is stored. */
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val isDeleted: Boolean get() = !loading && categoryName == null
    val canSave: Boolean get() = dirty && !saving
}

private data class DraftStatus(val saving: Boolean = false, val failed: Boolean = false)

/** Settings › Categories › one category: which attributes apply, required or not, in which order. */
@OptIn(ExperimentalUuidApi::class)
class CategoryAttributesViewModel(
    private val categoryId: String,
    private val attributeDao: AttributeDao,
    categoryDao: CategoryDao,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    /** Null while not edited: the stored list is shown. */
    private val draft = MutableStateFlow<List<CategoryAttributeInput>?>(null)
    private val status = MutableStateFlow(DraftStatus())

    val state: StateFlow<CategoryAttributesUiState> = combine(
        categoryDao.observeAll(),
        attributeDao.observeDefinitions(),
        attributeDao.observeCategoryAttributes(categoryId),
        draft,
        status,
    ) { categories, definitions, links, draft, status ->
        val stored = links.map { CategoryAttributeInput(it.link.attributeId, it.link.required) }
        val byId = definitions.associateBy { it.id }
        // A definition deleted meanwhile drops out of the draft
        val current = (draft ?: stored).filter { it.attributeId in byId }
        val used = current.map { it.attributeId }.toSet()
        CategoryAttributesUiState(
            loading = false,
            categoryName = categories.firstOrNull { it.id == categoryId }?.name,
            rows = current.map { CategoryAttributeRow(byId.getValue(it.attributeId), it.required) },
            available = definitions.filter { it.id !in used },
            dirty = current != stored,
            saving = status.saving,
            saveFailed = status.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryAttributesUiState())

    private fun edit(transform: (List<CategoryAttributeInput>) -> List<CategoryAttributeInput>) {
        if (status.value.saving) return
        val current = state.value.rows.map { CategoryAttributeInput(it.definition.id, it.required) }
        draft.value = transform(current)
        status.update { it.copy(failed = false) }
    }

    fun add(attributeId: String) = edit { it.withAttribute(attributeId) }
    fun remove(attributeId: String) = edit { it.withoutAttribute(attributeId) }
    fun setRequired(attributeId: String, required: Boolean) = edit { it.withRequired(attributeId, required) }
    fun moveUp(attributeId: String) = edit { it.moved(attributeId, -1) }
    fun moveDown(attributeId: String) = edit { it.moved(attributeId, 1) }

    fun discard() {
        draft.value = null
        status.value = DraftStatus()
    }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        val wanted = current.rows.map { CategoryAttributeInput(it.definition.id, it.required) }
        status.value = DraftStatus(saving = true)
        viewModelScope.launch {
            try {
                attributeDao.setCategoryAttributes(categoryId, wanted, clock.now().toEpochMilliseconds(), newId)
                draft.value = null
                status.value = DraftStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AttributeNotFoundException) {
                // Deleted meanwhile: the next state drops it from the draft, the user saves again
                status.value = DraftStatus()
            } catch (e: Exception) {
                status.value = DraftStatus(failed = true)
            }
        }
    }
}
