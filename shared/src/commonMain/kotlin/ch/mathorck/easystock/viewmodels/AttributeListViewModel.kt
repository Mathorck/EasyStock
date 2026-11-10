package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.MeasureFamily
import ch.mathorck.easystock.composables.attributes.MeasureUnits
import ch.mathorck.easystock.composables.attributes.canBecomeMeasure
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.AttributeNotFoundException
import ch.mathorck.easystock.data.model.AttributeDataType
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

/** Schema limits (docs/schema.dbml). */
const val MAX_ATTRIBUTE_LABEL_LENGTH = 100
const val MAX_ATTRIBUTE_UNIT_LENGTH = 20
const val MAX_ATTRIBUTE_OPTION_LENGTH = 100

/** Errors of the catalogue forms (attributes and categories). */
enum class CatalogueError { REQUIRED, TOO_LONG, DUPLICATE, DELETED_NAME, NO_OPTIONS, DUPLICATE_OPTION }

/** Raw input of the attribute dialog. [optionsText] holds one enum option per line. */
data class AttributeForm(
    val label: String = "",
    val dataType: AttributeDataType = AttributeDataType.TEXT,
    val unit: String = "",
    val optionsText: String = "",
)

/** Copy with [type]. A measure gets a known unit: the typed one when known ("MM" -> mm), else the length default. */
fun AttributeForm.withType(type: AttributeDataType): AttributeForm =
    if (type == AttributeDataType.MEASURE) {
        copy(dataType = type, unit = MeasureUnits.find(unit)?.symbol ?: MeasureFamily.LENGTH.defaultUnit)
    } else {
        copy(dataType = type)
    }

/**
 * Applies the edit rules to this form (just changed) when [editing] is stored. A new attribute is not restricted.
 * - The type is locked, except number -> measure when the stored unit is empty or known ([canBecomeMeasure]).
 * - A number with a known unit becomes a measure in that unit: saved values keep their meaning.
 * - The unit of a stored measure is locked: values without unit are read in it.
 */
fun AttributeForm.lockedFor(editing: AttributeDefinition?): AttributeForm = when {
    editing == null -> this
    editing.dataType == AttributeDataType.MEASURE -> copy(dataType = AttributeDataType.MEASURE, unit = editing.unit.orEmpty())
    editing.dataType == AttributeDataType.NUMBER && dataType == AttributeDataType.MEASURE -> {
        val storedUnit = MeasureUnits.find(editing.unit)
        when {
            storedUnit != null -> copy(unit = storedUnit.symbol)
            editing.unit.isNullOrBlank() -> this
            else -> copy(dataType = AttributeDataType.NUMBER, unit = editing.unit)
        }
    }
    else -> copy(dataType = editing.dataType)
}

/** Types the attribute can have: all for a new one ([editing] null), number or measure for a convertible number. */
fun allowedTypes(editing: AttributeDefinition?): List<AttributeDataType> = when {
    editing == null -> AttributeDataType.entries
    editing.canBecomeMeasure() -> listOf(AttributeDataType.NUMBER, AttributeDataType.MEASURE)
    else -> listOf(editing.dataType)
}

/** True when the default unit of the measure in the form can't be chosen (stored measure, or number with a known unit). */
fun measureUnitLocked(editing: AttributeDefinition?): Boolean =
    editing != null && (editing.dataType == AttributeDataType.MEASURE || MeasureUnits.find(editing.unit) != null)

fun AttributeDefinition.toForm(): AttributeForm = AttributeForm(
    label = label,
    dataType = dataType,
    unit = unit.orEmpty(),
    optionsText = options.orEmpty().joinToString("\n"),
)

data class AttributeFormErrors(
    val label: CatalogueError? = null,
    val unit: CatalogueError? = null,
    val options: CatalogueError? = null,
) {
    val isValid: Boolean get() = label == null && unit == null && options == null
}

/** Options of [text]: one per line, trimmed, blank lines dropped. Order kept. */
fun parseAttributeOptions(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Checks the form. [definitions]: active definitions; [editingId]: the edited one, null for a new one.
 * Labels are unique among active definitions, ignoring case.
 */
fun AttributeForm.validate(definitions: List<AttributeDefinition>, editingId: String?): AttributeFormErrors {
    val labelValue = label.trim()
    val options = parseAttributeOptions(optionsText)
    return AttributeFormErrors(
        label = when {
            labelValue.isEmpty() -> CatalogueError.REQUIRED
            labelValue.length > MAX_ATTRIBUTE_LABEL_LENGTH -> CatalogueError.TOO_LONG
            definitions.any { it.id != editingId && it.label.trim().equals(labelValue, ignoreCase = true) } -> CatalogueError.DUPLICATE
            else -> null
        },
        unit = when {
            unit.trim().length > MAX_ATTRIBUTE_UNIT_LENGTH -> CatalogueError.TOO_LONG
            dataType == AttributeDataType.MEASURE && MeasureUnits.find(unit) == null -> CatalogueError.REQUIRED
            else -> null
        },
        options = when {
            dataType != AttributeDataType.ENUM -> null
            options.isEmpty() -> CatalogueError.NO_OPTIONS
            options.any { it.length > MAX_ATTRIBUTE_OPTION_LENGTH } -> CatalogueError.TOO_LONG
            options.toSet().size != options.size -> CatalogueError.DUPLICATE_OPTION
            else -> null
        },
    )
}

/** The open attribute dialog. [editing] is null for a new attribute. */
data class AttributeDialogState(
    val editing: AttributeDefinition?,
    val form: AttributeForm,
    val errors: AttributeFormErrors,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = errors.isValid && !saving
}

data class AttributeListUiState(
    val loading: Boolean = true,
    val definitions: List<AttributeDefinition> = emptyList(),
    val dialog: AttributeDialogState? = null,
    /** One-shot: articles renamed by the last edit (their category name template uses it). */
    val renamedCount: Int? = null,
)

private data class AttributeDialogInput(
    val editing: AttributeDefinition?,
    val form: AttributeForm,
    val saving: Boolean = false,
    val failed: Boolean = false,
)

/** Settings › Attributes: list, create, edit (label, unit, options) and soft delete. */
@OptIn(ExperimentalUuidApi::class)
class AttributeListViewModel(
    private val dao: AttributeDao,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    private val dialog = MutableStateFlow<AttributeDialogInput?>(null)
    private val renamed = MutableStateFlow<Int?>(null)

    val state: StateFlow<AttributeListUiState> = combine(dao.observeDefinitions(), dialog, renamed) { definitions, input, renamed ->
        AttributeListUiState(
            renamedCount = renamed,
            loading = false,
            definitions = definitions,
            dialog = input?.let {
                AttributeDialogState(
                    editing = it.editing,
                    form = it.form,
                    errors = it.form.validate(definitions, it.editing?.id),
                    saving = it.saving,
                    saveFailed = it.failed,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttributeListUiState())

    fun startCreate() {
        dialog.value = AttributeDialogInput(editing = null, form = AttributeForm())
    }

    fun startEdit(definition: AttributeDefinition) {
        dialog.value = AttributeDialogInput(editing = definition, form = definition.toForm())
    }

    /** The data type can only change on a new attribute, or from number to measure ([lockedFor]). */
    fun updateForm(transform: (AttributeForm) -> AttributeForm) {
        dialog.update { input ->
            input?.let {
                val form = transform(it.form).lockedFor(it.editing)
                it.copy(form = form, failed = false)
            }
        }
    }

    fun cancel() {
        dialog.value = null
    }

    fun consumeRenamed() {
        renamed.value = null
    }

    fun save() {
        val input = dialog.value ?: return
        val current = state.value.dialog ?: return
        if (!current.canSave || current.form != input.form) return
        dialog.value = input.copy(saving = true, failed = false)
        viewModelScope.launch {
            try {
                val now = clock.now().toEpochMilliseconds()
                val form = input.form
                val label = form.label.trim()
                val unit = if (form.dataType == AttributeDataType.MEASURE) {
                    MeasureUnits.find(form.unit)?.symbol
                } else {
                    form.unit.trim().ifEmpty { null }
                }
                val options = parseAttributeOptions(form.optionsText).takeIf { form.dataType == AttributeDataType.ENUM }
                val editing = input.editing
                if (editing == null) {
                    dao.createDefinition(newId(), label, form.dataType, unit, options, now)
                } else {
                    val type = form.dataType.takeIf { it != editing.dataType }
                    renamed.value = dao.updateDefinition(editing.id, label, unit, options, now, type).takeIf { it > 0 }
                }
                if (dialog.value?.editing?.id == editing?.id) dialog.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: AttributeNotFoundException) {
                dialog.value = null
            } catch (e: Exception) {
                dialog.update { it?.copy(saving = false, failed = true) }
            }
        }
    }

    /** Soft-deletes the definition and its category links. Stored values are kept. */
    fun delete(definition: AttributeDefinition) {
        viewModelScope.launch {
            if (dialog.value?.editing?.id == definition.id) dialog.value = null
            try {
                dao.softDeleteDefinition(definition.id, clock.now().toEpochMilliseconds())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing deleted, the row stays in the list
            }
        }
    }
}
