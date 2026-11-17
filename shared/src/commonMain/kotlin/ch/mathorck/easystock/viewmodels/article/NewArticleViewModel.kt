package ch.mathorck.easystock.viewmodels.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dao.article.ArticleFormDao
import ch.mathorck.easystock.data.dao.attributes.AttributeDao
import ch.mathorck.easystock.data.dao.categories.CategoryDao
import ch.mathorck.easystock.enums.article.ArticleFormMode
import ch.mathorck.easystock.exceptions.article.DuplicateSkuException
import ch.mathorck.easystock.exceptions.categories.DeletedCategoryNameException
import ch.mathorck.easystock.services.images.ArticleImageStore
import ch.mathorck.easystock.services.images.StagedImages
import ch.mathorck.easystock.viewmodels.stock.SkuIndex
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        dao.observeSkus().map(::SkuIndex),
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
