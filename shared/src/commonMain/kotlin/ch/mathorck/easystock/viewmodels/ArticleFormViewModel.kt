package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.AttributeError
import ch.mathorck.easystock.composables.attributes.articleNameFromTemplate
import ch.mathorck.easystock.composables.attributes.validateAttributes
import ch.mathorck.easystock.data.db.ArticleChanges
import ch.mathorck.easystock.data.db.ArticleFormDao
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.db.DeletedCategoryNameException
import ch.mathorck.easystock.data.db.DuplicateSkuException
import ch.mathorck.easystock.data.db.SkuOwner
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.images.ArticleImageStore
import ch.mathorck.easystock.images.StagedImages
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.util.parseQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Max lengths from docs/schema.dbml
const val MAX_NAME_LENGTH = 255
const val MAX_SKU_LENGTH = 50
const val MAX_UNIT_LENGTH = 20
const val MAX_CATEGORY_NAME_LENGTH = 100

/** Unit of new articles, and the fixed unit of serial-tracked ones (counted in pieces). */
const val DEFAULT_UNIT = "pce"

enum class CategoryMode { NONE, EXISTING, NEW }

enum class FieldError { REQUIRED, TOO_LONG, DUPLICATE, DELETED_NAME, INVALID_QUANTITY, NOT_POSITIVE }

/**
 * Create: minimal form (type, name, category, unit). Edit: every field, the type is read-only.
 * Both use the same [ArticleForm] and [validate], hidden fields stay blank and are valid.
 */
enum class ArticleFormMode { CREATE, EDIT }

/** Raw input of the article form, as typed. */
data class ArticleForm(
    val tracking: Tracking = Tracking.QUANTITY,
    val name: String = "",
    val sku: String = "",
    val description: String = "",
    val unit: String = DEFAULT_UNIT,
    val categoryMode: CategoryMode = CategoryMode.NONE,
    val categoryId: String? = null,
    val categoryName: String = "",
    val threshold: String = "",
    /** Photo path (see [ch.mathorck.easystock.images.newImagePath]), null without photo. */
    val imagePath: String? = null,
    /** Custom attribute values, keyed by attribute key (see [ch.mathorck.easystock.composables.attributes.AttributeFields]). */
    val attributes: JsonObject? = null,
    /**
     * The name follows the template of the category (see [withTemplateName]). Typing a name clears it.
     * New articles start following it, so the name fills in once a category with a template is chosen.
     */
    val nameAuto: Boolean = true,
) {
    /** The unit that is saved: fixed for serial-tracked articles. */
    val effectiveUnit: String get() = if (tracking == Tracking.SERIAL) DEFAULT_UNIT else unit.trim()
}

/** Form prefilled with the stored values. */
fun Article.toForm(): ArticleForm = ArticleForm(
    tracking = tracking,
    name = name,
    sku = sku.orEmpty(),
    description = description.orEmpty(),
    unit = unit,
    categoryMode = if (categoryId == null) CategoryMode.NONE else CategoryMode.EXISTING,
    categoryId = categoryId,
    threshold = thresholdMilli?.let { formatQuantity(it) }.orEmpty(),
    imagePath = imagePath,
    attributes = attributes,
    nameAuto = nameAuto,
)

/**
 * The form as shown and saved: when it follows the template ([ArticleForm.nameAuto]) and the category has one
 * ([templateName] not null), the name is the rendered template.
 */
fun ArticleForm.withTemplateName(templateName: String?): ArticleForm =
    if (nameAuto && templateName != null && templateName != name) copy(name = templateName) else this

/** The typed [name]: the name no longer follows the template. */
fun ArticleForm.withTypedName(name: String): ArticleForm = copy(name = name, nameAuto = false)

/** Name of the article from the template of its category, null when the chosen category has no valid template. */
fun ArticleForm.templateName(categories: List<Category>, attributes: List<CategoryAttributeWithDefinition>): String? {
    if (categoryMode != CategoryMode.EXISTING) return null
    val category = categories.firstOrNull { it.id == categoryId } ?: return null
    val template = category.nameTemplate ?: return null
    return articleNameFromTemplate(template, attributes.map { it.definition }, this.attributes, MAX_NAME_LENGTH, category.name)
}

/** One error per field, null when the field is valid. */
data class ArticleFormErrors(
    val name: FieldError? = null,
    val sku: FieldError? = null,
    val unit: FieldError? = null,
    val category: FieldError? = null,
    val categoryName: FieldError? = null,
    val threshold: FieldError? = null,
) {
    val isValid: Boolean get() = listOf(name, sku, unit, category, categoryName, threshold).all { it == null }
}

/**
 * Checks the form, for a new article ([selfId] null) or an existing one.
 * [currentCategoryId]: stored category of the edited article, it stays valid even if soft-deleted meanwhile.
 * [skus]: SKUs of all articles, soft-deleted ones included. [categories]: active categories.
 * [rejectedSku] / [rejectedCategoryName]: values the database refused on the last save.
 */
fun ArticleForm.validate(
    selfId: String?,
    currentCategoryId: String?,
    skus: List<SkuOwner>,
    categories: List<Category>,
    rejectedSku: String? = null,
    rejectedCategoryName: String? = null,
): ArticleFormErrors {
    val skuValue = sku.trim()
    val categoryValue = categoryName.trim()
    return ArticleFormErrors(
        name = requiredText(name, MAX_NAME_LENGTH),
        sku = when {
            skuValue.isEmpty() -> null
            skuValue.length > MAX_SKU_LENGTH -> FieldError.TOO_LONG
            skus.any { it.id != selfId && it.sku.equals(skuValue, ignoreCase = true) } -> FieldError.DUPLICATE
            rejectedSku != null && rejectedSku.equals(skuValue, ignoreCase = true) -> FieldError.DUPLICATE
            else -> null
        },
        unit = if (tracking == Tracking.SERIAL) null else requiredText(unit, MAX_UNIT_LENGTH),
        category = if (
            categoryMode == CategoryMode.EXISTING &&
            (categoryId == null || categoryId != currentCategoryId) &&
            categories.none { it.id == categoryId }
        ) {
            FieldError.REQUIRED
        } else {
            null
        },
        categoryName = when {
            categoryMode != CategoryMode.NEW -> null
            requiredText(categoryName, MAX_CATEGORY_NAME_LENGTH) != null -> requiredText(categoryName, MAX_CATEGORY_NAME_LENGTH)
            rejectedCategoryName != null && rejectedCategoryName.equals(categoryValue, ignoreCase = true) ->
                FieldError.DELETED_NAME
            else -> null
        },
        threshold = if (tracking != Tracking.SERIAL && threshold.isNotBlank() && parseQuantity(threshold) == null) FieldError.INVALID_QUANTITY else null,
    )
}

/** Checks the edit form of [article]. */
fun ArticleForm.validate(
    article: Article,
    skus: List<SkuOwner>,
    categories: List<Category>,
    rejectedSku: String? = null,
    rejectedCategoryName: String? = null,
): ArticleFormErrors = validate(article.id, article.categoryId, skus, categories, rejectedSku, rejectedCategoryName)

private fun requiredText(value: String, maxLength: Int): FieldError? = when {
    value.isBlank() -> FieldError.REQUIRED
    value.trim().length > maxLength -> FieldError.TOO_LONG
    else -> null
}

/** The chosen category id, and the category to create first (if any). */
private data class CategoryChoice(val id: String?, val newCategory: Category?)

/** A new category with the name of an active one (ignoring case) reuses it. */
private fun ArticleForm.chooseCategory(categories: List<Category>, now: Long, newId: () -> String): CategoryChoice =
    when (categoryMode) {
        CategoryMode.NONE -> CategoryChoice(null, null)
        CategoryMode.EXISTING -> CategoryChoice(requireNotNull(categoryId) { "No category" }, null)
        CategoryMode.NEW -> {
            val wanted = categoryName.trim()
            val active = categories.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
            if (active != null) {
                CategoryChoice(active.id, null)
            } else {
                val created = Category(id = newId(), name = wanted, createdAt = now, updatedAt = now)
                CategoryChoice(created.id, created)
            }
        }
    }

/** Serial-tracked articles (tools) have no low-stock alert: the threshold is cleared. */
private fun ArticleForm.thresholdMilli(): Long? =
    if (tracking == Tracking.SERIAL) null else threshold.takeIf { it.isNotBlank() }?.let { requireNotNull(parseQuantity(it)) { "Invalid threshold" } }

/** What to save on edit: the changed fields, and the category to create first (if any). */
data class ArticleUpdate(val changes: ArticleChanges, val newCategory: Category?)

/**
 * Builds the update. Call only on a valid form (with [withTemplateName] applied). A blank SKU or description
 * becomes null. [followsTemplate]: the category has a template and the name follows it.
 */
fun ArticleForm.toUpdate(categories: List<Category>, now: Long, newId: () -> String, followsTemplate: Boolean = false): ArticleUpdate {
    val category = chooseCategory(categories, now, newId)
    val changes = ArticleChanges(
        name = name.trim(),
        nameAuto = followsTemplate,
        sku = sku.trim().ifEmpty { null },
        description = description.trim().ifEmpty { null },
        categoryId = category.id,
        unit = effectiveUnit,
        thresholdMilli = thresholdMilli(),
        imagePath = imagePath,
        attributes = attributes,
    )
    return ArticleUpdate(changes, category.newCategory)
}

/** What to save on create: the article, and the category to create first (if any). */
data class NewArticle(val article: Article, val newCategory: Category?)

/** Builds the new article. Same rules as [toUpdate]. [tagId]: NFC tag to link (see [ch.mathorck.easystock.nfc.tagIdOf]). */
fun ArticleForm.toNewArticle(
    categories: List<Category>,
    now: Long,
    newId: () -> String,
    followsTemplate: Boolean = false,
    tagId: String? = null,
): NewArticle {
    val category = chooseCategory(categories, now, newId)
    val article = Article(
        id = newId(),
        sku = sku.trim().ifEmpty { null },
        name = name.trim(),
        nameAuto = followsTemplate,
        description = description.trim().ifEmpty { null },
        categoryId = category.id,
        tracking = tracking,
        unit = effectiveUnit,
        thresholdMilli = thresholdMilli(),
        imagePath = imagePath,
        attributes = attributes,
        barcode = tagId,
        createdAt = now,
        updatedAt = now,
    )
    return NewArticle(article, category.newCategory)
}

data class ArticleFormUiState(
    val mode: ArticleFormMode,
    /** The form as shown, with the template name applied ([withTemplateName]). */
    val form: ArticleForm,
    /** Active categories. */
    val categories: List<Category>,
    /** Name of the stored category, also when it is soft-deleted. Null on create. */
    val currentCategoryName: String? = null,
    val errors: ArticleFormErrors,
    val saving: Boolean = false,
    /** The last save failed for an unexpected reason. */
    val saveFailed: Boolean = false,
    /** Attributes of the chosen category, in order. */
    val attributes: List<CategoryAttributeWithDefinition> = emptyList(),
    val attributeErrors: Map<String, AttributeError> = emptyMap(),
    /** The name rendered from the category template, null when the category has none. */
    val templateName: String? = null,
    /** Create only: the NFC tag the new article gets (opened from an unknown scanned tag). */
    val tagId: String? = null,
) {
    val canSave: Boolean get() = errors.isValid && attributeErrors.isEmpty() && !saving

    /** The name is the rendered template. */
    val followsTemplate: Boolean get() = templateName != null && form.nameAuto
}

/** Attributes of the category chosen in the form, with the category id they belong to. */
data class FormAttributes(val categoryId: String?, val attributes: List<CategoryAttributeWithDefinition>)

/** Attributes of the category chosen in [form], following its changes. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun formAttributes(form: Flow<ArticleForm?>, attributeDao: AttributeDao): Flow<FormAttributes> =
    form.map { it?.takeIf { f -> f.categoryMode == CategoryMode.EXISTING }?.categoryId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(FormAttributes(null, emptyList()))
            else attributeDao.observeCategoryAttributes(id).map { FormAttributes(id, it) }
        }

/** The attributes of [loaded] when they belong to the category chosen in the form (empty while another loads). */
fun ArticleForm.attributesOf(loaded: FormAttributes): List<CategoryAttributeWithDefinition> =
    if (categoryMode == CategoryMode.EXISTING && loaded.categoryId == categoryId) loaded.attributes else emptyList()

/**
 * Builds the UI state of the article form from the [raw] input: the template name applied, the fields
 * ([validate], called on the shown form) and the attributes checked.
 */
internal fun articleFormState(
    mode: ArticleFormMode,
    raw: ArticleForm,
    categories: List<Category>,
    loaded: FormAttributes,
    status: FormSaveStatus,
    currentCategoryName: String? = null,
    validate: (ArticleForm) -> ArticleFormErrors,
): ArticleFormUiState {
    val attributes = raw.attributesOf(loaded)
    val templateName = raw.templateName(categories, attributes)
    val form = raw.withTemplateName(templateName)
    return ArticleFormUiState(
        mode = mode,
        form = form,
        categories = categories,
        currentCategoryName = currentCategoryName,
        errors = validate(form),
        saving = status.saving,
        saveFailed = status.failed,
        attributes = attributes,
        attributeErrors = validateAttributes(attributes, form.attributes),
        templateName = templateName,
    )
}

/** Save progress of the article form, and the values the database refused. */
internal data class FormSaveStatus(
    val saving: Boolean = false,
    val failed: Boolean = false,
    val rejectedSku: String? = null,
    val rejectedCategoryName: String? = null,
)

/** Deletes photo files in the background. */
internal fun ViewModel.deleteImages(images: ArticleImageStore, paths: Set<String>) {
    if (paths.isEmpty()) return
    viewModelScope.launch { paths.forEach { images.delete(it) } }
}

/**
 * The "New article" form. Closed while [state] is null. [created] is the id of the article just saved.
 * A photo is copied as soon as it is taken (see [onImageImported]), the unused copies are deleted on close.
 */
@OptIn(ExperimentalUuidApi::class)
class NewArticleViewModel(
    private val dao: ArticleFormDao,
    categoryDao: CategoryDao,
    attributeDao: AttributeDao,
    private val images: ArticleImageStore,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    private val form = MutableStateFlow<ArticleForm?>(null)
    private val status = MutableStateFlow(FormSaveStatus())
    private val createdId = MutableStateFlow<String?>(null)
    private var latest: ArticleFormUiState? = null
    private var staged = StagedImages()

    /** NFC tag of the new article, set by [start] before the form opens. */
    private var tagId: String? = null

    /** Bumped when the form opens or closes, so a late save result is ignored. */
    private var generation = 0

    /** One-shot: read it, then call [consumeCreated]. */
    val created: StateFlow<String?> = createdId.asStateFlow()

    val state: StateFlow<ArticleFormUiState?> = combine(
        form,
        categoryDao.observeAll(),
        dao.observeSkus(),
        status,
        formAttributes(form, attributeDao),
    ) { form, categories, skus, status, attributes ->
        form?.let {
            articleFormState(ArticleFormMode.CREATE, it, categories, attributes, status) { shown ->
                shown.validate(null, null, skus, categories, status.rejectedSku, status.rejectedCategoryName)
            }.copy(tagId = tagId)
        }.also { latest = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opens an empty form. The article gets the NFC tag [tagId] when set. */
    fun start(tagId: String? = null) {
        if (form.value != null) cancel()
        generation++
        status.value = FormSaveStatus()
        staged = StagedImages()
        this.tagId = tagId
        form.value = ArticleForm()
    }

    /** A photo was copied for the form. Deleted right away if the form is already closed. */
    fun onImageImported(path: String) {
        if (form.value == null) {
            deleteImages(images, setOf(path))
            return
        }
        staged += path
        update { it.copy(imagePath = path) }
    }

    /** [transform] gets the form as shown (template name applied), so the name is kept when the template goes away. */
    fun update(transform: (ArticleForm) -> ArticleForm) {
        val templateName = latest?.templateName
        form.update { it?.withTemplateName(templateName)?.let(transform) }
        status.update { it.copy(failed = false) }
    }

    /**
     * Closes without saving. The photos taken meanwhile are deleted, unless a save is still running:
     * the save then deletes what it doesn't keep.
     */
    fun cancel() {
        if (!status.value.saving) deleteImages(images, staged.toDeleteOnCancel())
        close()
    }

    private fun close() {
        generation++
        form.value = null
        status.value = FormSaveStatus()
        staged = StagedImages()
        tagId = null
    }

    fun consumeCreated() {
        createdId.value = null
    }

    /** Saves the form. On success the form closes and [created] holds the new id. */
    fun save() {
        val current = latest ?: return
        if (!current.canSave || status.value.saving || current.form != form.value?.withTemplateName(current.templateName)) return
        status.update { it.copy(saving = true, failed = false) }
        val startedIn = generation
        val session = staged
        viewModelScope.launch {
            var saved = false
            try {
                val now = clock.now().toEpochMilliseconds()
                val new = current.form.toNewArticle(current.categories, now, newId, current.followsTemplate, current.tagId)
                val stored = dao.createArticle(new.article, new.newCategory)
                saved = true
                // Same form: photos may have been added meanwhile, they are in the current set
                val photos = if (startedIn == generation) staged else session
                deleteImages(images, photos.toDeleteOnSave(stored.imagePath))
                if (startedIn == generation) {
                    close()
                    createdId.value = stored.id
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateSkuException) {
                if (startedIn == generation) status.update { it.copy(saving = false, rejectedSku = e.sku) }
            } catch (e: DeletedCategoryNameException) {
                if (startedIn == generation) status.update { it.copy(saving = false, rejectedCategoryName = e.name) }
            } catch (e: Exception) {
                if (startedIn == generation) status.update { it.copy(saving = false, failed = true) }
            } finally {
                // Closed while saving and nothing saved: the photos of that form are unused
                if (!saved && startedIn != generation) deleteImages(images, session.toDeleteOnCancel())
            }
        }
    }
}
