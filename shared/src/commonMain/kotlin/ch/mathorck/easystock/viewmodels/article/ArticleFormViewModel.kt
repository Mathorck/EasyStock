package ch.mathorck.easystock.viewmodels.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dao.article.ArticleChanges
import ch.mathorck.easystock.data.dao.attributes.AttributeDao
import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.enums.article.ArticleFormMode
import ch.mathorck.easystock.enums.article.FieldError
import ch.mathorck.easystock.enums.article.Tracking
import ch.mathorck.easystock.enums.attributes.validateAttributes
import ch.mathorck.easystock.enums.categories.CategoryMode
import ch.mathorck.easystock.services.attributes.articleNameFromTemplate
import ch.mathorck.easystock.services.images.ArticleImageStore
import ch.mathorck.easystock.util.OrderUrlCheck
import ch.mathorck.easystock.util.checkOrderUrl
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.util.orderUrlOrNull
import ch.mathorck.easystock.util.parseQuantity
import ch.mathorck.easystock.viewmodels.attributes.validate
import ch.mathorck.easystock.viewmodels.categories.CategoryChoice
import ch.mathorck.easystock.viewmodels.stock.SkuIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Max lengths from docs/schema.dbml
const val MAX_NAME_LENGTH = 255

const val MAX_SKU_LENGTH = 50

const val MAX_UNIT_LENGTH = 20

const val MAX_CATEGORY_NAME_LENGTH = 100

/** Unit of new articles, and the fixed unit of serial-tracked ones (counted in pieces). */
const val DEFAULT_UNIT = "pce"

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
    orderUrl = orderUrl.orEmpty(),
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

/**
 * Checks the form, for a new article ([selfId] null) or an existing one.
 * [currentCategoryId]: stored category of the edited article, it stays valid even if soft-deleted meanwhile.
 * [skus]: SKUs of all articles, soft-deleted ones included. [categories]: active categories.
 * [rejectedSku] / [rejectedCategoryName]: values the database refused on the last save.
 */
fun ArticleForm.validate(
    selfId: String?,
    currentCategoryId: String?,
    skus: SkuIndex,
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
            skus.isTaken(skuValue, selfId) -> FieldError.DUPLICATE
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
        orderUrl = when (checkOrderUrl(orderUrl)) {
            OrderUrlCheck.TooLong -> FieldError.TOO_LONG
            OrderUrlCheck.Invalid -> FieldError.INVALID_URL
            else -> null
        },
    )
}

/** Checks the edit form of [article]. */
fun ArticleForm.validate(
    article: Article,
    skus: SkuIndex,
    categories: List<Category>,
    rejectedSku: String? = null,
    rejectedCategoryName: String? = null,
): ArticleFormErrors = validate(article.id, article.categoryId, skus, categories, rejectedSku, rejectedCategoryName)

private fun requiredText(value: String, maxLength: Int): FieldError? = when {
    value.isBlank() -> FieldError.REQUIRED
    value.trim().length > maxLength -> FieldError.TOO_LONG
    else -> null
}

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

/**
 * Builds the update. Call only on a valid form (with [withTemplateName] applied). A blank SKU, description or order link
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
        orderUrl = orderUrlOrNull(orderUrl),
    )
    return ArticleUpdate(changes, category.newCategory)
}

/** Builds the new article. Same rules as [toUpdate]. [tagId]: NFC tag to link (see [ch.mathorck.easystock.services.nfc.tagIdOf]). */
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
        orderUrl = orderUrlOrNull(orderUrl),
        barcode = tagId,
        createdAt = now,
        updatedAt = now,
    )
    return NewArticle(article, category.newCategory)
}

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

/** Deletes photo files in the background. */
internal fun ViewModel.deleteImages(images: ArticleImageStore, paths: Set<String>) {
    if (paths.isEmpty()) return
    viewModelScope.launch { paths.forEach { images.delete(it) } }
}
