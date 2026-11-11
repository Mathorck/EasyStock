package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.NameTemplateError
import ch.mathorck.easystock.composables.attributes.articleNameFromTemplate
import ch.mathorck.easystock.composables.attributes.sampleAttributeValues
import ch.mathorck.easystock.composables.attributes.validateNameTemplate
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.AttributeNotFoundException
import ch.mathorck.easystock.data.db.CategoryAttributeInput
import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.db.CategoryNotFoundException
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
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

// ---------- Name template (pure) ----------

/** Name template input of the category screen, checked against the attributes of the draft. */
data class NameTemplateState(
    /** As typed, or the stored template while not edited (empty = no template). */
    val text: String = "",
    val error: NameTemplateError? = null,
    /** Name built from the template for the [previewSource]; null without (valid) template. */
    val preview: String? = null,
    val previewSource: PreviewSource = PreviewSource.SAMPLE,
    /** Active articles of the category with a typed name, they don't follow the template. */
    val manualNameCount: Int = 0,
    /** Save also makes those articles follow the template. */
    val applyToAll: Boolean = false,
) {
    enum class PreviewSource { ARTICLE, SAMPLE }
}

/**
 * The template part of the screen. [sampleArticle]: last edited article of the category with values; without it,
 * example values ([sampleAttributeValues]) are used. [categoryName] fills {category}.
 */
fun nameTemplateState(
    text: String,
    definitions: List<AttributeDefinition>,
    sampleArticle: Article?,
    manualNameCount: Int,
    applyToAll: Boolean,
    categoryName: String? = null,
): NameTemplateState {
    val error = validateNameTemplate(text, definitions.map { it.key })
    val source = if (sampleArticle?.attributes != null) NameTemplateState.PreviewSource.ARTICLE else NameTemplateState.PreviewSource.SAMPLE
    val values = sampleArticle?.attributes ?: sampleAttributeValues(definitions)
    return NameTemplateState(
        text = text,
        error = error,
        preview = if (error == null) articleNameFromTemplate(text, definitions, values, MAX_NAME_LENGTH, categoryName) else null,
        previewSource = source,
        manualNameCount = manualNameCount,
        applyToAll = applyToAll && text.isNotBlank(),
    )
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
    val template: NameTemplateState = NameTemplateState(),
    /** The draft differs from what is stored. */
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** One-shot: how many articles the last save renamed. Read it, then call [CategoryAttributesViewModel.consumeRenamed]. */
    val renamedCount: Int? = null,
) {
    val isDeleted: Boolean get() = !loading && categoryName == null
    val canSave: Boolean get() = dirty && !saving && template.error == null
}

private data class DraftStatus(val saving: Boolean = false, val failed: Boolean = false, val renamed: Int? = null)

/** Edited template: null while not edited (the stored one is shown). */
private data class TemplateDraft(val text: String? = null, val applyToAll: Boolean = false)

/** Stored state of the category: its links, template and articles. */
private data class StoredCategory(
    val category: Category?,
    val links: List<CategoryAttributeWithDefinition>,
    val sampleArticle: Article?,
    val manualNameCount: Int,
)

/**
 * Settings › Categories › one category: which attributes apply, required or not, in which order,
 * and the name template of its articles. Everything is saved together.
 */
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
    private val templateDraft = MutableStateFlow(TemplateDraft())
    private val status = MutableStateFlow(DraftStatus())

    private val stored = combine(
        categoryDao.observeAll(),
        attributeDao.observeCategoryAttributes(categoryId),
        attributeDao.observeSampleArticle(categoryId),
        attributeDao.observeManualNameCount(categoryId),
    ) { categories, links, sample, manual -> StoredCategory(categories.firstOrNull { it.id == categoryId }, links, sample, manual) }

    val state: StateFlow<CategoryAttributesUiState> = combine(
        stored,
        attributeDao.observeDefinitions(),
        draft,
        templateDraft,
        status,
    ) { stored, definitions, draft, templateDraft, status ->
        val storedLinks = stored.links.map { CategoryAttributeInput(it.link.attributeId, it.link.required) }
        val byId = definitions.associateBy { it.id }
        // A definition deleted meanwhile drops out of the draft
        val current = (draft ?: storedLinks).filter { it.attributeId in byId }
        val used = current.map { it.attributeId }.toSet()
        val rows = current.map { CategoryAttributeRow(byId.getValue(it.attributeId), it.required) }
        val storedTemplate = stored.category?.nameTemplate.orEmpty()
        val templateText = templateDraft.text ?: storedTemplate
        val template = nameTemplateState(
            templateText, rows.map { it.definition }, stored.sampleArticle, stored.manualNameCount, templateDraft.applyToAll,
            stored.category?.name,
        )
        CategoryAttributesUiState(
            loading = false,
            categoryName = stored.category?.name,
            rows = rows,
            available = definitions.filter { it.id !in used },
            template = template,
            dirty = current != storedLinks || templateText.trim() != storedTemplate.trim() || template.applyToAll,
            saving = status.saving,
            saveFailed = status.failed,
            renamedCount = status.renamed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryAttributesUiState())

    private fun edit(transform: (List<CategoryAttributeInput>) -> List<CategoryAttributeInput>) {
        if (status.value.saving) return
        val current = state.value.rows.map { CategoryAttributeInput(it.definition.id, it.required) }
        draft.value = transform(current)
        status.value = DraftStatus()
    }

    fun add(attributeId: String) = edit { it.withAttribute(attributeId) }
    fun remove(attributeId: String) = edit { it.withoutAttribute(attributeId) }
    fun setRequired(attributeId: String, required: Boolean) = edit { it.withRequired(attributeId, required) }
    fun moveUp(attributeId: String) = edit { it.moved(attributeId, -1) }
    fun moveDown(attributeId: String) = edit { it.moved(attributeId, 1) }

    fun updateTemplate(text: String) {
        if (status.value.saving) return
        templateDraft.update { it.copy(text = text) }
        status.value = DraftStatus()
    }

    fun setApplyToAll(apply: Boolean) {
        if (status.value.saving) return
        templateDraft.update { it.copy(applyToAll = apply) }
    }

    fun consumeRenamed() {
        status.update { it.copy(renamed = null) }
    }

    fun discard() {
        draft.value = null
        templateDraft.value = TemplateDraft()
        status.value = DraftStatus()
    }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        val wanted = current.rows.map { CategoryAttributeInput(it.definition.id, it.required) }
        status.value = DraftStatus(saving = true)
        viewModelScope.launch {
            try {
                val renamed = attributeDao.saveCategorySettings(
                    categoryId = categoryId,
                    attributes = wanted,
                    nameTemplate = current.template.text,
                    applyToAll = current.template.applyToAll,
                    now = clock.now().toEpochMilliseconds(),
                    newId = newId,
                )
                draft.value = null
                templateDraft.value = TemplateDraft()
                status.value = DraftStatus(renamed = renamed.takeIf { it > 0 })
            } catch (e: CancellationException) {
                throw e
            } catch (e: AttributeNotFoundException) {
                // Deleted meanwhile: the next state drops it from the draft, the user saves again
                status.value = DraftStatus()
            } catch (e: CategoryNotFoundException) {
                // Deleted meanwhile: the screen goes back
                status.value = DraftStatus()
            } catch (e: Exception) {
                status.value = DraftStatus(failed = true)
            }
        }
    }
}
