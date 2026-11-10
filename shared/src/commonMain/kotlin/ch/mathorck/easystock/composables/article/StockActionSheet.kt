@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import ch.mathorck.easystock.composables.LocationIcon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.data.db.UnitAction
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.LocationChoice
import ch.mathorck.easystock.viewmodels.StockActionContext
import ch.mathorck.easystock.viewmodels.StockActionError
import ch.mathorck.easystock.viewmodels.StockActionForm
import ch.mathorck.easystock.viewmodels.StockActionKind
import ch.mathorck.easystock.viewmodels.StockActionUiState
import ch.mathorck.easystock.viewmodels.buildLocationOptions
import ch.mathorck.easystock.viewmodels.reasonsFor
import ch.mathorck.easystock.viewmodels.toLocationTree
import ch.mathorck.easystock.viewmodels.validate
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.stock_action_at
import easystock.shared.generated.resources.stock_action_available
import easystock.shared.generated.resources.stock_action_cancel
import easystock.shared.generated.resources.stock_action_comment
import easystock.shared.generated.resources.stock_action_comment_hint
import easystock.shared.generated.resources.stock_action_destination
import easystock.shared.generated.resources.stock_action_error_exceeds
import easystock.shared.generated.resources.stock_action_error_not_positive
import easystock.shared.generated.resources.stock_action_error_quantity
import easystock.shared.generated.resources.stock_action_error_same_location
import easystock.shared.generated.resources.stock_action_error_save
import easystock.shared.generated.resources.item_edit_title
import easystock.shared.generated.resources.stock_action_error_serial_taken
import easystock.shared.generated.resources.stock_action_error_too_long
import easystock.shared.generated.resources.stock_action_error_unit
import easystock.shared.generated.resources.stock_action_from
import easystock.shared.generated.resources.stock_action_lend_hint
import easystock.shared.generated.resources.stock_action_location
import easystock.shared.generated.resources.stock_action_location_name
import easystock.shared.generated.resources.stock_action_location_parent
import easystock.shared.generated.resources.stock_action_location_top_level
import easystock.shared.generated.resources.stock_action_lose_hint
import easystock.shared.generated.resources.stock_action_mode_existing
import easystock.shared.generated.resources.stock_action_mode_new
import easystock.shared.generated.resources.stock_action_no_locations
import easystock.shared.generated.resources.stock_action_no_serial
import easystock.shared.generated.resources.stock_action_not_in_stock
import easystock.shared.generated.resources.stock_action_quantity
import easystock.shared.generated.resources.stock_action_reason
import easystock.shared.generated.resources.stock_action_repair_hint
import easystock.shared.generated.resources.stock_action_save
import easystock.shared.generated.resources.stock_action_title_add
import easystock.shared.generated.resources.stock_action_title_add_unit
import easystock.shared.generated.resources.stock_action_title_end_repair
import easystock.shared.generated.resources.stock_action_title_found
import easystock.shared.generated.resources.stock_action_title_lend
import easystock.shared.generated.resources.stock_action_title_lose
import easystock.shared.generated.resources.stock_action_title_move
import easystock.shared.generated.resources.stock_action_title_move_unit
import easystock.shared.generated.resources.stock_action_title_remove
import easystock.shared.generated.resources.stock_action_title_repair
import easystock.shared.generated.resources.stock_action_title_return
import easystock.shared.generated.resources.stock_action_unit_label
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Bottom sheet for one stock action (add, remove, move, unit actions). [unit] is the article unit, e.g. "pce". */
@Composable
fun StockActionSheet(
    state: StockActionUiState,
    unit: String,
    onFormChange: ((StockActionForm) -> StockActionForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        StockActionContent(state = state, unit = unit, onFormChange = onFormChange, onCancel = close, onSave = onSave)
    }
}

@Composable
fun StockActionContent(
    state: StockActionUiState,
    unit: String,
    onFormChange: ((StockActionForm) -> StockActionForm) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(form.title), style = MaterialTheme.typography.titleLarge)

        if (form.kind == StockActionKind.MOVE_UNIT || form.kind == StockActionKind.UNIT_STATUS) {
            Info(stringResource(Res.string.stock_action_unit_label, state.unitSerial ?: stringResource(Res.string.stock_action_no_serial)))
        }
        if (form.kind == StockActionKind.EDIT_UNIT && state.sourcePath == null) Info(stringResource(Res.string.stock_action_not_in_stock))
        state.sourcePath?.takeIf { form.kind != StockActionKind.ADD_UNIT }?.let { path ->
            val moving = form.kind == StockActionKind.MOVE || form.kind == StockActionKind.MOVE_UNIT
            Info(stringResource(if (moving) Res.string.stock_action_from else Res.string.stock_action_at, path))
        }
        if (form.kind == StockActionKind.REMOVE || form.kind == StockActionKind.MOVE) {
            state.availableMilli?.let { Info(stringResource(Res.string.stock_action_available, "${formatQuantity(it)} $unit")) }
        }
        form.unitAction?.hint?.let { Info(stringResource(it)) }

        if (form.hasUnitProperties) ItemPropertiesFields(state, onFormChange)
        if (form.needsTarget) TargetPicker(state, onFormChange)

        if (form.needsQuantity) {
            ActionField(
                value = form.quantity,
                onValueChange = { v -> onFormChange { it.copy(quantity = v) } },
                label = stringResource(Res.string.stock_action_quantity),
                error = state.errors.quantity,
                errorArg = state.availableMilli?.let { "${formatQuantity(it)} $unit" },
                suffix = unit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        val reasons = reasonsFor(form.kind)
        if (reasons.isNotEmpty()) {
            Text(stringResource(Res.string.stock_action_reason), style = MaterialTheme.typography.labelLarge)
            Choice(
                options = reasons,
                selected = form.reason,
                label = { stringResource(it.historyLabel) },
                onSelect = { reason -> onFormChange { it.copy(reason = reason) } },
            )
        }
        ActionField(
            value = form.comment,
            onValueChange = { v -> onFormChange { it.copy(comment = v) } },
            label = stringResource(Res.string.stock_action_comment),
            error = null,
            supporting = stringResource(Res.string.stock_action_comment_hint),
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        if (state.errors.unit != null) ErrorText(stringResource(Res.string.stock_action_error_unit))
        if (state.saveFailed) ErrorText(stringResource(Res.string.stock_action_error_save))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.stock_action_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.stock_action_save)) }
        }
    }
}

@Composable
private fun TargetPicker(state: StockActionUiState, onFormChange: ((StockActionForm) -> StockActionForm) -> Unit) {
    val form = state.form
    val moving = form.kind == StockActionKind.MOVE || form.kind == StockActionKind.MOVE_UNIT
    Choice(
        options = LocationChoice.entries,
        selected = form.targetMode,
        label = {
            stringResource(if (it == LocationChoice.EXISTING) Res.string.stock_action_mode_existing else Res.string.stock_action_mode_new)
        },
        onSelect = { mode -> onFormChange { it.copy(targetMode = mode) } },
    )
    val options = state.locations.map { PickerOption(it.location.id, it.location.name, it.depth, it.location.icon, it.location.color, showIcon = true) }
    val paths = remember(state.locations) { buildLocationOptions(state.locations.map { it.location }).associate { it.id to it.path } }
    when (form.targetMode) {
        LocationChoice.EXISTING -> if (options.isEmpty()) {
            Info(stringResource(Res.string.stock_action_no_locations))
        } else {
            Picker(
                label = stringResource(if (moving) Res.string.stock_action_destination else Res.string.stock_action_location),
                value = form.targetLocationId?.let { paths[it] }.orEmpty(),
                options = options,
                error = state.errors.location?.takeIf { it != StockActionError.REQUIRED }?.let { errorText(it, null) },
                onSelect = { id -> onFormChange { it.copy(targetLocationId = id) } },
            )
        }
        LocationChoice.NEW -> {
            ActionField(
                value = form.newLocationName,
                onValueChange = { v -> onFormChange { it.copy(newLocationName = v) } },
                label = stringResource(Res.string.stock_action_location_name),
                error = state.errors.locationName,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (options.isNotEmpty()) {
                val topLevel = stringResource(Res.string.stock_action_location_top_level)
                Picker(
                    label = stringResource(Res.string.stock_action_location_parent),
                    value = form.newLocationParentId?.let { paths[it] } ?: topLevel,
                    options = listOf(PickerOption(TOP_LEVEL, topLevel)) + options,
                    error = null,
                    onSelect = { id -> onFormChange { it.copy(newLocationParentId = id.takeUnless { id == TOP_LEVEL }) } },
                )
            }
        }
    }
}

private const val TOP_LEVEL = "#top"

@Composable
internal fun Info(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/** Text field with [error] below it. A missing required value is not shown, Save stays disabled. */
@Composable
internal fun ActionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: StockActionError?,
    errorArg: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val message = error?.let { errorText(it, errorArg) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = message != null,
        supportingText = (message ?: supporting)?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun errorText(error: StockActionError, arg: String?): String? = when (error) {
    StockActionError.REQUIRED -> null
    StockActionError.INVALID_QUANTITY -> stringResource(Res.string.stock_action_error_quantity)
    StockActionError.NOT_POSITIVE -> stringResource(Res.string.stock_action_error_not_positive)
    StockActionError.EXCEEDS_STOCK -> stringResource(Res.string.stock_action_error_exceeds, arg.orEmpty())
    StockActionError.TOO_LONG -> stringResource(Res.string.stock_action_error_too_long)
    StockActionError.DUPLICATE_SERIAL -> stringResource(Res.string.stock_action_error_serial_taken)
    StockActionError.SAME_LOCATION -> stringResource(Res.string.stock_action_error_same_location)
}

@Composable
internal fun <T> Choice(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label(option), maxLines = 1)
            }
        }
    }
}

private data class PickerOption(
    val key: String,
    val text: String,
    val depth: Int = 0,
    val icon: String? = null,
    val color: String? = null,
    val showIcon: Boolean = false,
)

@Composable
private fun Picker(label: String, value: String, options: List<PickerOption>, error: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text, modifier = Modifier.padding(start = (option.depth * 16).dp)) },
                    leadingIcon = if (option.showIcon) {
                        { LocationIcon(option.icon, option.color) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.key)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ---------- Labels ----------

private val StockActionForm.title: StringResource
    get() = when (kind) {
        StockActionKind.ADD -> Res.string.stock_action_title_add
        StockActionKind.REMOVE -> Res.string.stock_action_title_remove
        StockActionKind.MOVE -> Res.string.stock_action_title_move
        StockActionKind.ADD_UNIT -> Res.string.stock_action_title_add_unit
        StockActionKind.MOVE_UNIT -> Res.string.stock_action_title_move_unit
        StockActionKind.EDIT_UNIT -> Res.string.item_edit_title
        StockActionKind.UNIT_STATUS -> when (unitAction) {
            UnitAction.LEND, null -> Res.string.stock_action_title_lend
            UnitAction.RETURN -> Res.string.stock_action_title_return
            UnitAction.REPAIR -> Res.string.stock_action_title_repair
            UnitAction.END_REPAIR -> Res.string.stock_action_title_end_repair
            UnitAction.LOSE -> Res.string.stock_action_title_lose
            UnitAction.FOUND -> Res.string.stock_action_title_found
        }
    }

private val UnitAction.hint: StringResource?
    get() = when (this) {
        UnitAction.LEND -> Res.string.stock_action_lend_hint
        UnitAction.REPAIR -> Res.string.stock_action_repair_hint
        UnitAction.LOSE -> Res.string.stock_action_lose_hint
        else -> null
    }

// ---------- Previews ----------

private val previewLocations = listOf(
    Location(id = "l1", name = "Workshop", createdAt = 0, updatedAt = 0),
    Location(id = "l2", name = "Shelf A", parentId = "l1", createdAt = 0, updatedAt = 0),
    Location(id = "l3", name = "Van", createdAt = 0, updatedAt = 0),
)

@Preview(showBackground = true)
@Composable
private fun StockActionMovePreview() {
    val form = StockActionForm(kind = StockActionKind.MOVE, sourceLocationId = "l2", quantity = "50", targetLocationId = "l3")
    val context = StockActionContext(mapOf("l2" to 40_000L), setOf("l1", "l2", "l3"), emptyList(), emptyList())
    EasyStockTheme {
        Surface {
            StockActionContent(
                state = StockActionUiState(
                    form = form,
                    errors = form.validate(context),
                    locations = previewLocations.toLocationTree(),
                    sourcePath = "Workshop › Shelf A",
                    availableMilli = 40_000,
                    unitSerial = null,
                ),
                unit = "pce",
                onFormChange = {},
                onCancel = {},
                onSave = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StockActionRemovePreview() {
    val form = StockActionForm(kind = StockActionKind.REMOVE, sourceLocationId = "l3", reason = MovementReason.CONSUMPTION)
    EasyStockTheme(darkTheme = true) {
        Surface {
            StockActionContent(
                state = StockActionUiState(
                    form = form,
                    errors = form.validate(StockActionContext(mapOf("l3" to 2_500L), setOf("l3"), emptyList(), emptyList())),
                    locations = previewLocations.toLocationTree(),
                    sourcePath = "Van",
                    availableMilli = 2_500,
                    unitSerial = null,
                ),
                unit = "pce",
                onFormChange = {},
                onCancel = {},
                onSave = {},
            )
        }
    }
}
