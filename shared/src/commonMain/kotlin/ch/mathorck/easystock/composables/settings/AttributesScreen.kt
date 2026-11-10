package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.attributes.MeasureFamily
import ch.mathorck.easystock.composables.attributes.MeasureUnits
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.AttributeDialogState
import ch.mathorck.easystock.viewmodels.AttributeForm
import ch.mathorck.easystock.viewmodels.AttributeListUiState
import ch.mathorck.easystock.viewmodels.AttributeListViewModel
import ch.mathorck.easystock.viewmodels.allowedTypes
import ch.mathorck.easystock.viewmodels.measureUnitLocked
import ch.mathorck.easystock.viewmodels.validate
import ch.mathorck.easystock.viewmodels.withType
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.attributes_add
import easystock.shared.generated.resources.attributes_cancel
import easystock.shared.generated.resources.attributes_convert_hint
import easystock.shared.generated.resources.attributes_convert_unavailable
import easystock.shared.generated.resources.attributes_default_unit
import easystock.shared.generated.resources.attributes_family
import easystock.shared.generated.resources.attributes_family_force
import easystock.shared.generated.resources.attributes_family_length
import easystock.shared.generated.resources.attributes_family_mass
import easystock.shared.generated.resources.attributes_family_volume
import easystock.shared.generated.resources.attributes_measure_hint
import easystock.shared.generated.resources.attributes_measure_locked
import easystock.shared.generated.resources.attributes_delete
import easystock.shared.generated.resources.attributes_delete_message
import easystock.shared.generated.resources.attributes_delete_title
import easystock.shared.generated.resources.attributes_edit_title
import easystock.shared.generated.resources.attributes_empty
import easystock.shared.generated.resources.attributes_error_save
import easystock.shared.generated.resources.attributes_label
import easystock.shared.generated.resources.attributes_new_title
import easystock.shared.generated.resources.attributes_options
import easystock.shared.generated.resources.attributes_options_hint
import easystock.shared.generated.resources.attributes_save
import easystock.shared.generated.resources.attributes_title
import easystock.shared.generated.resources.attributes_type
import easystock.shared.generated.resources.attributes_type_bool
import easystock.shared.generated.resources.attributes_type_date
import easystock.shared.generated.resources.attributes_type_enum
import easystock.shared.generated.resources.attributes_type_locked
import easystock.shared.generated.resources.attributes_type_measure
import easystock.shared.generated.resources.attributes_type_number
import easystock.shared.generated.resources.attributes_type_text
import easystock.shared.generated.resources.attributes_unit
import easystock.shared.generated.resources.attributes_unit_hint
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_delete
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Settings › Attributes. [onBack] runs on the arrow and the system back. */
@Composable
fun AttributesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val viewModel = viewModel(key = "settings-attributes") { AttributeListViewModel(db.attributeDao()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back = {
        viewModel.cancel()
        onBack()
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = back)

    AttributesContent(
        state = state,
        onBack = back,
        onAdd = viewModel::startCreate,
        onEdit = viewModel::startEdit,
        onDelete = viewModel::delete,
        onRenamedSeen = viewModel::consumeRenamed,
        modifier = modifier,
    )
    state.dialog?.let { dialog ->
        AttributeDialog(
            state = dialog,
            onFormChange = viewModel::updateForm,
            onDismiss = viewModel::cancel,
            onSave = viewModel::save,
        )
    }
}

@Composable
fun AttributesContent(
    state: AttributeListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AttributeDefinition) -> Unit,
    onDelete: (AttributeDefinition) -> Unit,
    onRenamedSeen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var toDelete by remember { mutableStateOf<AttributeDefinition?>(null) }
    SettingsSubpage(
        title = stringResource(Res.string.attributes_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.attributes_add))
            }
        },
    ) {
        state.renamedCount?.let { RenamedArticlesMessage(it, onRenamedSeen) }
        if (!state.loading && state.definitions.isEmpty()) {
            EmptyListHint(stringResource(Res.string.attributes_empty))
        }
        LazyColumn {
            items(state.definitions, key = { it.id }) { definition ->
                ListItem(
                    headlineContent = { Text(definition.label) },
                    supportingContent = { Text(definition.summary(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { toDelete = definition }) {
                            Icon(painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.attributes_delete))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { onEdit(definition) },
                )
            }
        }
    }
    toDelete?.let { definition ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.attributes_delete_title),
            message = stringResource(Res.string.attributes_delete_message, definition.label),
            onConfirm = {
                toDelete = null
                onDelete(definition)
            },
            onDismiss = { toDelete = null },
        )
    }
}

/** "Number · N", "List of choices · Red, Blue". */
@Composable
private fun AttributeDefinition.summary(): String {
    val details = when (dataType) {
        AttributeDataType.ENUM -> options.orEmpty().joinToString(", ")
        else -> unit.orEmpty()
    }
    val type = stringResource(dataType.label())
    return if (details.isBlank()) type else "$type · $details"
}

internal fun AttributeDataType.label(): StringResource = when (this) {
    AttributeDataType.TEXT -> Res.string.attributes_type_text
    AttributeDataType.NUMBER -> Res.string.attributes_type_number
    AttributeDataType.BOOL -> Res.string.attributes_type_bool
    AttributeDataType.DATE -> Res.string.attributes_type_date
    AttributeDataType.ENUM -> Res.string.attributes_type_enum
    AttributeDataType.MEASURE -> Res.string.attributes_type_measure
}

internal fun MeasureFamily.label(): StringResource = when (this) {
    MeasureFamily.LENGTH -> Res.string.attributes_family_length
    MeasureFamily.MASS -> Res.string.attributes_family_mass
    MeasureFamily.VOLUME -> Res.string.attributes_family_volume
    MeasureFamily.FORCE -> Res.string.attributes_family_force
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttributeDialog(
    state: AttributeDialogState,
    onFormChange: ((AttributeForm) -> AttributeForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val form = state.form
    val isNew = state.editing == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) Res.string.attributes_new_title else Res.string.attributes_edit_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                val labelError = state.errors.label?.message()
                OutlinedTextField(
                    value = form.label,
                    onValueChange = { value -> onFormChange { it.copy(label = value) } },
                    label = { Text(stringResource(Res.string.attributes_label)) },
                    isError = labelError != null,
                    supportingText = labelError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val types = allowedTypes(state.editing)
                val typeHint = when {
                    isNew -> null
                    types.size > 1 -> stringResource(Res.string.attributes_convert_hint)
                    state.editing?.dataType == AttributeDataType.NUMBER ->
                        stringResource(Res.string.attributes_convert_unavailable, MeasureUnits.all.joinToString(", ") { it.symbol })
                    else -> stringResource(Res.string.attributes_type_locked)
                }
                Dropdown(
                    label = stringResource(Res.string.attributes_type),
                    selected = form.dataType,
                    options = types,
                    optionLabel = { type -> type?.let { stringResource(it.label()) }.orEmpty() },
                    enabled = types.size > 1,
                    supportingText = typeHint,
                    onSelected = { type -> onFormChange { it.withType(type) } },
                )
                if (form.dataType == AttributeDataType.MEASURE) {
                    val unit = MeasureUnits.find(form.unit)
                    val locked = measureUnitLocked(state.editing)
                    Dropdown(
                        label = stringResource(Res.string.attributes_family),
                        selected = unit?.family,
                        options = MeasureFamily.entries,
                        optionLabel = { family -> family?.let { stringResource(it.label()) }.orEmpty() },
                        enabled = !locked,
                        onSelected = { family -> onFormChange { it.copy(unit = family.defaultUnit) } },
                    )
                    Dropdown(
                        label = stringResource(Res.string.attributes_default_unit),
                        selected = unit,
                        options = unit?.let { MeasureUnits.of(it.family) }.orEmpty(),
                        optionLabel = { it?.symbol.orEmpty() },
                        enabled = !locked,
                        supportingText = stringResource(
                            if (locked) Res.string.attributes_measure_locked else Res.string.attributes_measure_hint,
                        ),
                        onSelected = { u -> onFormChange { it.copy(unit = u.symbol) } },
                    )
                }
                if (form.dataType == AttributeDataType.TEXT || form.dataType == AttributeDataType.NUMBER) {
                    val unitError = state.errors.unit?.message()
                    OutlinedTextField(
                        value = form.unit,
                        onValueChange = { value -> onFormChange { it.copy(unit = value) } },
                        label = { Text(stringResource(Res.string.attributes_unit)) },
                        isError = unitError != null,
                        supportingText = { Text(unitError ?: stringResource(Res.string.attributes_unit_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (form.dataType == AttributeDataType.ENUM) {
                    val optionsError = state.errors.options?.message()
                    OutlinedTextField(
                        value = form.optionsText,
                        onValueChange = { value -> onFormChange { it.copy(optionsText = value) } },
                        label = { Text(stringResource(Res.string.attributes_options)) },
                        isError = optionsError != null,
                        supportingText = { Text(optionsError ?: stringResource(Res.string.attributes_options_hint)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.saveFailed) {
                    Text(
                        stringResource(Res.string.attributes_error_save),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.attributes_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.attributes_cancel)) }
        },
    )
}

/** Read-only dropdown. [options] shows nothing to choose when it has one entry and [enabled] is false. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: @Composable (T?) -> String,
    enabled: Boolean,
    onSelected: (T) -> Unit,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ---------- Previews ----------

private fun previewDefinition(id: String, label: String, type: AttributeDataType, unit: String? = null, options: List<String>? = null) =
    AttributeDefinition(id = id, key = id, label = label, dataType = type, unit = unit, options = options, createdAt = 0, updatedAt = 0)

private val previewDefinitions = listOf(
    previewDefinition("breaking_strength", "Breaking strength", AttributeDataType.NUMBER, unit = "N"),
    previewDefinition("color", "Color", AttributeDataType.ENUM, options = listOf("Red", "Blue", "Green")),
    previewDefinition("dynamic", "Dynamic", AttributeDataType.BOOL),
    previewDefinition("length", "Length", AttributeDataType.MEASURE, unit = "m"),
)

@Preview(showBackground = true)
@Composable
private fun AttributesContentPreview() {
    EasyStockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AttributesContent(
                state = AttributeListUiState(loading = false, definitions = previewDefinitions),
                onBack = {}, onAdd = {}, onEdit = {}, onDelete = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AttributeDialogEditPreview() {
    EasyStockTheme {
        val editing = previewDefinitions.first()
        val form = AttributeForm(label = "Breaking strength", dataType = AttributeDataType.NUMBER, unit = "N")
        AttributeDialog(
            state = AttributeDialogState(editing = editing, form = form, errors = form.validate(previewDefinitions, editing.id)),
            onFormChange = {}, onDismiss = {}, onSave = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AttributesEmptyDarkPreview() {
    EasyStockTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AttributesContent(
                state = AttributeListUiState(loading = false),
                onBack = {}, onAdd = {}, onEdit = {}, onDelete = {},
            )
        }
    }
}
