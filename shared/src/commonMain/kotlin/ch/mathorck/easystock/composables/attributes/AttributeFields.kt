package ch.mathorck.easystock.composables.attributes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.theme.EasyStockTheme
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.attributes_cancel
import easystock.shared.generated.resources.attributes_clear
import easystock.shared.generated.resources.attributes_error_date
import easystock.shared.generated.resources.attributes_error_invalid
import easystock.shared.generated.resources.attributes_error_number
import easystock.shared.generated.resources.attributes_error_option
import easystock.shared.generated.resources.attributes_error_required
import easystock.shared.generated.resources.attributes_error_too_long
import easystock.shared.generated.resources.attributes_field_required
import easystock.shared.generated.resources.attributes_none
import easystock.shared.generated.resources.attributes_ok
import easystock.shared.generated.resources.attributes_pick_date
import easystock.shared.generated.resources.attributes_value_unit
import easystock.shared.generated.resources.ic_calendar
import easystock.shared.generated.resources.ic_close
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Inputs for custom attribute values, one per attribute, in the given order.
 * Owner-agnostic: works for `article.attributes` and `item.attributes`.
 *
 * - [attributes]: attributes of the category (`AttributeDao.observeCategoryAttributes`), already ordered.
 * - [values]: the stored JSON object (null = no values).
 * - [onValuesChange]: the whole new object. Keys of other attributes are kept; an emptied field removes
 *   its key; null when no key is left. Save it as is.
 * - [errors]: from [validateAttributes], keyed by attribute key. Pass an empty map to show no errors
 *   (e.g. before the first save attempt).
 *
 * Input per type: text field (text), decimal field with the unit as suffix (number, "," or "."),
 * switch (bool, unset = off), date picker (date, ISO), dropdown (enum), decimal field with a unit dropdown
 * (measure, the definition's default unit preselected).
 * A number that does not parse yet is kept as a string in [values]: [validateAttributes] reports it.
 *
 * [inherited]: values of the article, for fields that override them (a unit): shown as the placeholder of an
 * empty field. [overrides] marks that mode: a bool shows the inherited state until set, with a clear button.
 */
@Composable
fun AttributeFields(
    attributes: List<CategoryAttributeWithDefinition>,
    values: JsonObject?,
    onValuesChange: (JsonObject?) -> Unit,
    errors: Map<String, AttributeError>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inherited: JsonObject? = null,
    overrides: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attributes.forEach { attribute ->
            val definition = attribute.definition
            val key = definition.key
            val label = if (attribute.link.required && definition.dataType != AttributeDataType.BOOL) {
                stringResource(Res.string.attributes_field_required, definition.label)
            } else {
                definition.label
            }
            val value = values.attributeValue(key)
            val onValueChange: (JsonElement?) -> Unit = { onValuesChange(values.withAttributeValue(key, it)) }
            val errorText = errors[key]?.message()
            val inheritedValue = inherited.attributeValue(key)
            val placeholder = if (definition.dataType == AttributeDataType.MEASURE) {
                readMeasure(definition, inheritedValue)?.text
            } else {
                inheritedValue?.let { attributeInputText(it) }?.takeIf { it.isNotEmpty() }
            }
            when (definition.dataType) {
                AttributeDataType.TEXT -> TextAttributeField(label, definition, value, onValueChange, errorText, enabled, placeholder)
                AttributeDataType.NUMBER -> NumberAttributeField(label, definition, value, onValueChange, errorText, enabled, placeholder)
                AttributeDataType.BOOL ->
                    if (overrides) {
                        OverrideBoolAttributeField(label, value, inheritedValue, onValueChange, enabled)
                    } else {
                        BoolAttributeField(label, value, onValueChange, enabled)
                    }
                AttributeDataType.DATE -> DateAttributeField(label, value, onValueChange, errorText, enabled, placeholder)
                AttributeDataType.ENUM -> EnumAttributeField(label, definition, value, onValueChange, errorText, enabled, placeholder)
                AttributeDataType.MEASURE -> MeasureAttributeField(label, definition, value, onValueChange, errorText, enabled, placeholder)
            }
        }
    }
}

@Composable
private fun AttributeError.message(): String = when (this) {
    AttributeError.REQUIRED -> stringResource(Res.string.attributes_error_required)
    AttributeError.TOO_LONG -> stringResource(Res.string.attributes_error_too_long)
    AttributeError.INVALID_NUMBER -> stringResource(Res.string.attributes_error_number)
    AttributeError.INVALID_DATE -> stringResource(Res.string.attributes_error_date)
    AttributeError.INVALID_OPTION -> stringResource(Res.string.attributes_error_option)
    AttributeError.INVALID -> stringResource(Res.string.attributes_error_invalid)
}

@Composable
private fun TextAttributeField(
    label: String,
    definition: AttributeDefinition,
    value: JsonElement?,
    onValueChange: (JsonElement?) -> Unit,
    errorText: String?,
    enabled: Boolean,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = attributeInputText(value),
        onValueChange = { onValueChange(attributeValueFromText(AttributeDataType.TEXT, it)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        suffix = definition.unit?.let { { Text(it) } },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberAttributeField(
    label: String,
    definition: AttributeDefinition,
    value: JsonElement?,
    onValueChange: (JsonElement?) -> Unit,
    errorText: String?,
    enabled: Boolean,
    placeholder: String? = null,
) {
    // Keeps the text as typed ("1,50"): the stored value is normalized (1.5)
    var text by rememberSaveable(definition.key) { mutableStateOf(attributeInputText(value)) }
    if (attributeValueFromText(AttributeDataType.NUMBER, text) != value) {
        // Changed from outside (reset, other article): show the stored value
        text = attributeInputText(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(attributeValueFromText(AttributeDataType.NUMBER, it))
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        suffix = definition.unit?.let { { Text(it) } },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasureAttributeField(
    label: String,
    definition: AttributeDefinition,
    value: JsonElement?,
    onValueChange: (JsonElement?) -> Unit,
    errorText: String?,
    enabled: Boolean,
    placeholder: String? = null,
) {
    val defaultUnit = definition.measureUnit
    val units = defaultUnit?.let { MeasureUnits.of(it.family) }.orEmpty()
    // Number as typed and unit chosen: the unit stays while the number is empty
    var text by rememberSaveable(definition.key) { mutableStateOf(measureInputText(value)) }
    var unit by rememberSaveable(definition.key) { mutableStateOf(measureInputUnit(value, defaultUnit?.symbol.orEmpty())) }
    if (measureValueFromInput(text, unit) != value) {
        // Changed from outside (reset, other article): show the stored value. A plain number (saved before the
        // attribute was a measure) lands here on every pass, harmlessly: same text, default unit.
        text = measureInputText(value)
        unit = measureInputUnit(value, defaultUnit?.symbol.orEmpty())
    }
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(measureValueFromInput(it, unit))
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it },
            modifier = Modifier.width(112.dp),
        ) {
            OutlinedTextField(
                value = unit,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.attributes_value_unit)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                units.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.symbol) },
                        onClick = {
                            expanded = false
                            unit = option.symbol
                            onValueChange(measureValueFromInput(text, option.symbol))
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoolAttributeField(label: String, value: JsonElement?, onValueChange: (JsonElement?) -> Unit, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = attributeBool(value),
            onCheckedChange = { onValueChange(JsonPrimitive(it)) },
            enabled = enabled,
        )
    }
}

/** Bool of a unit: shows the article value until the unit has its own, the cross removes the override. */
@Composable
private fun OverrideBoolAttributeField(
    label: String,
    value: JsonElement?,
    inherited: JsonElement?,
    onValueChange: (JsonElement?) -> Unit,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            IconButton(onClick = { onValueChange(null) }, enabled = enabled) {
                Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.attributes_clear))
            }
        }
        Switch(
            checked = attributeBool(value ?: inherited),
            onCheckedChange = { onValueChange(JsonPrimitive(it)) },
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateAttributeField(
    label: String,
    value: JsonElement?,
    onValueChange: (JsonElement?) -> Unit,
    errorText: String?,
    enabled: Boolean,
    placeholder: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val date = attributeDate(value)
    OutlinedTextField(
        value = attributeInputText(value),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(placeholder ?: "YYYY-MM-DD") },
        trailingIcon = {
            Row {
                if (value != null) {
                    IconButton(onClick = { onValueChange(null) }, enabled = enabled) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.attributes_clear))
                    }
                }
                IconButton(onClick = { showPicker = true }, enabled = enabled) {
                    Icon(painterResource(Res.drawable.ic_calendar), contentDescription = stringResource(Res.string.attributes_pick_date))
                }
            }
        },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    if (showPicker) {
        // The picker works with UTC midnight millis
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date?.let { it.toEpochDays() * MILLIS_PER_DAY })
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    pickerState.selectedDateMillis?.let {
                        onValueChange(attributeDateValue(LocalDate.fromEpochDays(it.floorDiv(MILLIS_PER_DAY))))
                    }
                }) { Text(stringResource(Res.string.attributes_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.attributes_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumAttributeField(
    label: String,
    definition: AttributeDefinition,
    value: JsonElement?,
    onValueChange: (JsonElement?) -> Unit,
    errorText: String?,
    enabled: Boolean,
    placeholder: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = attributeInputText(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.attributes_none)) },
                onClick = {
                    expanded = false
                    onValueChange(null)
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            definition.options.orEmpty().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onValueChange(JsonPrimitive(option))
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ---------- Previews ----------

/** Example from the user: category "Ropes". Preview data only, nothing is hard-coded in the app. */
private fun previewAttribute(
    key: String,
    label: String,
    type: AttributeDataType,
    unit: String? = null,
    options: List<String>? = null,
    required: Boolean = false,
    order: Int,
) = CategoryAttributeWithDefinition(
    link = CategoryAttribute(
        id = "link-$key", categoryId = "ropes", attributeId = key,
        required = required, sortOrder = order, createdAt = 0, updatedAt = 0,
    ),
    definition = AttributeDefinition(
        id = key, key = key, label = label, dataType = type,
        unit = unit, options = options, createdAt = 0, updatedAt = 0,
    ),
)

private val previewRopeAttributes = listOf(
    previewAttribute("breaking_strength", "Breaking strength", AttributeDataType.NUMBER, unit = "N", required = true, order = 0),
    previewAttribute("length", "Length", AttributeDataType.MEASURE, unit = "m", order = 1),
    previewAttribute("color", "Color", AttributeDataType.ENUM, options = listOf("Red", "Blue", "Green"), order = 2),
    previewAttribute("dynamic", "Dynamic", AttributeDataType.BOOL, order = 3),
    previewAttribute("inspected_on", "Inspected on", AttributeDataType.DATE, order = 4),
    previewAttribute("note", "Note", AttributeDataType.TEXT, order = 5),
)

@Composable
private fun AttributeFieldsPreviewHost(values: JsonObject?, showErrors: Boolean) {
    var current by remember { mutableStateOf(values) }
    Surface(color = MaterialTheme.colorScheme.background) {
    AttributeFields(
        attributes = previewRopeAttributes,
        values = current,
        onValuesChange = { current = it },
        errors = if (showErrors) validateAttributes(previewRopeAttributes, current) else emptyMap(),
        modifier = Modifier.padding(16.dp),
    )
    }
}

@Preview(showBackground = true)
@Composable
private fun AttributeFieldsPreview() {
    EasyStockTheme {
        AttributeFieldsPreviewHost(
            values = buildJsonObject {
                put("breaking_strength", 22000)
                put("length", 60)
                put("color", "Blue")
                put("dynamic", true)
                put("inspected_on", "2026-11-10")
            },
            showErrors = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AttributeFieldsErrorsPreview() {
    EasyStockTheme(darkTheme = true) {
        AttributeFieldsPreviewHost(
            values = buildJsonObject {
                put("length", "12,x")
                put("color", "Yellow")
            },
            showErrors = true,
        )
    }
}
