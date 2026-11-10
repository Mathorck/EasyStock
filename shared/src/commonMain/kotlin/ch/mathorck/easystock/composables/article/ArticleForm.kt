@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.ArticleForm
import ch.mathorck.easystock.viewmodels.ArticleFormMode
import ch.mathorck.easystock.viewmodels.ArticleFormUiState
import ch.mathorck.easystock.viewmodels.CategoryMode
import ch.mathorck.easystock.viewmodels.FieldError
import ch.mathorck.easystock.viewmodels.toForm
import ch.mathorck.easystock.viewmodels.validate
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.article_category
import easystock.shared.generated.resources.article_category_name
import easystock.shared.generated.resources.article_category_new
import easystock.shared.generated.resources.article_category_none
import easystock.shared.generated.resources.article_description
import easystock.shared.generated.resources.article_edit_title
import easystock.shared.generated.resources.article_error_category_deleted
import easystock.shared.generated.resources.article_error_quantity
import easystock.shared.generated.resources.article_error_save
import easystock.shared.generated.resources.article_error_sku_taken
import easystock.shared.generated.resources.article_error_too_long
import easystock.shared.generated.resources.article_form_close
import easystock.shared.generated.resources.article_form_create_hint
import easystock.shared.generated.resources.article_form_section_attributes
import easystock.shared.generated.resources.article_form_section_info
import easystock.shared.generated.resources.article_form_section_stock
import easystock.shared.generated.resources.article_form_section_type
import easystock.shared.generated.resources.article_form_title_new
import easystock.shared.generated.resources.article_form_type_consumable
import easystock.shared.generated.resources.article_form_type_consumable_hint
import easystock.shared.generated.resources.article_form_type_locked
import easystock.shared.generated.resources.article_form_type_tool
import easystock.shared.generated.resources.article_form_type_tool_hint
import easystock.shared.generated.resources.article_form_unit_hint
import easystock.shared.generated.resources.article_name
import easystock.shared.generated.resources.article_save
import easystock.shared.generated.resources.article_sku
import easystock.shared.generated.resources.article_stock_hint
import easystock.shared.generated.resources.article_threshold
import easystock.shared.generated.resources.article_threshold_hint
import easystock.shared.generated.resources.article_unit
import easystock.shared.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private typealias FormChange = ((ArticleForm) -> ArticleForm) -> Unit

/**
 * Full-screen article form, for create and edit (see [ArticleFormMode]).
 * [onDismiss] closes it without saving (close icon or system back).
 * [attributes]: custom attribute fields, shown in edit mode only.
 */
@Composable
fun ArticleFormDialog(
    state: ArticleFormUiState,
    onFormChange: FormChange,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    attributes: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ArticleFormScreen(state, onFormChange, onDismiss, onSave, attributes = attributes)
    }
}

/** Edit form of an article, opened from the detail screen. The stock is not editable here. */
@Composable
fun ArticleEditSheet(
    state: ArticleFormUiState,
    onFormChange: FormChange,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    attributes: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ArticleFormDialog(state, onFormChange, onDismiss, onSave, attributes)
}

@Composable
fun ArticleFormScreen(
    state: ArticleFormUiState,
    onFormChange: FormChange,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    attributes: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val title = when (state.mode) {
        ArticleFormMode.CREATE -> stringResource(Res.string.article_form_title_new)
        ArticleFormMode.EDIT -> stringResource(Res.string.article_edit_title)
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.article_form_close))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.article_save)) }
                },
            )
            ArticleFormContent(state, onFormChange, attributes = attributes)
        }
    }
}

@Composable
fun ArticleFormContent(
    state: ArticleFormUiState,
    onFormChange: FormChange,
    modifier: Modifier = Modifier,
    attributes: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val form = state.form
    val errors = state.errors
    val isEdit = state.mode == ArticleFormMode.EDIT
    val isConsumable = form.tracking == Tracking.QUANTITY
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Section(stringResource(Res.string.article_form_section_type)) {
            TypeSection(form.tracking, editable = !isEdit, onSelect = { t -> onFormChange { it.copy(tracking = t) } })
        }

        Section(stringResource(Res.string.article_form_section_info)) {
            FormField(
                value = form.name,
                onValueChange = { v -> onFormChange { it.copy(name = v) } },
                label = stringResource(Res.string.article_name),
                error = errors.name,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (isEdit) {
                FormField(
                    value = form.sku,
                    onValueChange = { v -> onFormChange { it.copy(sku = v) } },
                    label = stringResource(Res.string.article_sku),
                    error = errors.sku,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                FormField(
                    value = form.description,
                    onValueChange = { v -> onFormChange { it.copy(description = v) } },
                    label = stringResource(Res.string.article_description),
                    error = null,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            CategoryField(state, onFormChange)
        }

        // Create: a tool has no stock setting (fixed unit), so the section is left out
        if (isEdit || isConsumable) {
            Section(stringResource(Res.string.article_form_section_stock)) {
                if (isConsumable) {
                    FormField(
                        value = form.unit,
                        onValueChange = { v -> onFormChange { it.copy(unit = v) } },
                        label = stringResource(Res.string.article_unit),
                        error = errors.unit,
                        supporting = stringResource(Res.string.article_form_unit_hint),
                    )
                }
                if (isEdit) {
                    FormField(
                        value = form.threshold,
                        onValueChange = { v -> onFormChange { it.copy(threshold = v) } },
                        label = stringResource(Res.string.article_threshold),
                        error = errors.threshold,
                        suffix = form.effectiveUnit.ifEmpty { null },
                        supporting = stringResource(Res.string.article_threshold_hint),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    HintText(stringResource(Res.string.article_stock_hint))
                }
            }
        }

        // TODO(attributes): the custom attribute fields are plugged in through [attributes] (edit mode only).
        if (isEdit && attributes != null) {
            Section(stringResource(Res.string.article_form_section_attributes), content = attributes)
        }

        if (!isEdit) HintText(stringResource(Res.string.article_form_create_hint))

        if (state.saveFailed) {
            Text(
                stringResource(Res.string.article_error_save),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        content()
    }
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Create: two selectable cards. Edit: only the stored type, read-only. */
@Composable
private fun TypeSection(tracking: Tracking, editable: Boolean, onSelect: (Tracking) -> Unit) {
    if (editable) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            Tracking.entries.forEach { option ->
                TypeOption(option, selected = option == tracking, onClick = { onSelect(option) })
            }
        }
    } else {
        TypeOption(tracking, selected = true, onClick = null)
        HintText(stringResource(Res.string.article_form_type_locked))
    }
}

@Composable
private fun TypeOption(tracking: Tracking, selected: Boolean, onClick: (() -> Unit)?) {
    val (title, hint) = when (tracking) {
        Tracking.QUANTITY -> Res.string.article_form_type_consumable to Res.string.article_form_type_consumable_hint
        Tracking.SERIAL -> Res.string.article_form_type_tool to Res.string.article_form_type_tool_hint
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.secondary else colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick) else Modifier,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            if (onClick != null) RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = if (onClick != null) 12.dp else 0.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryField(state: ArticleFormUiState, onFormChange: FormChange) {
    val form = state.form
    val noneText = stringResource(Res.string.article_category_none)
    val newText = stringResource(Res.string.article_category_new)
    val options = listOf(CATEGORY_NONE to noneText) + state.categories.map { it.id to it.name } + (CATEGORY_NEW to newText)
    CategoryDropdown(
        label = stringResource(Res.string.article_category),
        value = when (form.categoryMode) {
            CategoryMode.NONE -> noneText
            CategoryMode.NEW -> newText
            // The stored category can be soft-deleted, so not in the list: show its name anyway
            CategoryMode.EXISTING -> state.categories.firstOrNull { it.id == form.categoryId }?.name
                ?: state.currentCategoryName.orEmpty()
        },
        options = options,
        onSelect = { key ->
            onFormChange {
                when (key) {
                    CATEGORY_NONE -> it.copy(categoryMode = CategoryMode.NONE, categoryId = null)
                    CATEGORY_NEW -> it.copy(categoryMode = CategoryMode.NEW, categoryId = null)
                    else -> it.copy(categoryMode = CategoryMode.EXISTING, categoryId = key)
                }
            }
        },
    )
    if (form.categoryMode == CategoryMode.NEW) {
        FormField(
            value = form.categoryName,
            onValueChange = { v -> onFormChange { it.copy(categoryName = v) } },
            label = stringResource(Res.string.article_category_name),
            error = state.errors.categoryName,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

private const val CATEGORY_NONE = "#none"
private const val CATEGORY_NEW = "#new"

/** Text field that shows [error] below it. A missing required value is not shown as an error, Save stays disabled. */
@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: FieldError?,
    suffix: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val errorText = error?.message()
    val supportingText = errorText ?: supporting
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorText != null,
        supportingText = supportingText?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 6,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FieldError.message(): String? = when (this) {
    FieldError.TOO_LONG -> stringResource(Res.string.article_error_too_long)
    FieldError.DUPLICATE -> stringResource(Res.string.article_error_sku_taken)
    FieldError.DELETED_NAME -> stringResource(Res.string.article_error_category_deleted)
    FieldError.INVALID_QUANTITY -> stringResource(Res.string.article_error_quantity)
    // Required: Save stays disabled. Not positive: not used here (0 is a valid threshold).
    FieldError.REQUIRED, FieldError.NOT_POSITIVE -> null
}

@Composable
private fun CategoryDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ---------- Previews ----------

private val previewArticle = Article(
    id = "a1", sku = "BLT-8", name = "Bolt M8", categoryId = "c1", thresholdMilli = 50_000, createdAt = 0, updatedAt = 0,
)
private val previewCategories = listOf(Category(id = "c1", name = "Hardware", createdAt = 0, updatedAt = 0))

private fun previewState(mode: ArticleFormMode, form: ArticleForm) = ArticleFormUiState(
    mode = mode,
    form = form,
    categories = previewCategories,
    currentCategoryName = if (mode == ArticleFormMode.EDIT) "Hardware" else null,
    errors = if (mode == ArticleFormMode.EDIT) {
        form.validate(previewArticle, emptyList(), previewCategories, rejectedSku = "TAKEN")
    } else {
        form.validate(null, null, emptyList(), previewCategories)
    },
)

@Composable
private fun ArticleFormPreview(mode: ArticleFormMode, form: ArticleForm, dark: Boolean) {
    EasyStockTheme(darkTheme = dark) {
        ArticleFormScreen(state = previewState(mode, form), onFormChange = {}, onClose = {}, onSave = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleFormCreatePreview() {
    ArticleFormPreview(ArticleFormMode.CREATE, ArticleForm(name = "Cable tie"), dark = false)
}

@Preview(showBackground = true)
@Composable
private fun ArticleFormCreateToolDarkPreview() {
    ArticleFormPreview(ArticleFormMode.CREATE, ArticleForm(tracking = Tracking.SERIAL), dark = true)
}

@Preview(showBackground = true)
@Composable
private fun ArticleFormEditPreview() {
    ArticleFormPreview(ArticleFormMode.EDIT, previewArticle.toForm(), dark = false)
}

@Preview(showBackground = true)
@Composable
private fun ArticleFormEditErrorsPreview() {
    ArticleFormPreview(
        ArticleFormMode.EDIT,
        previewArticle.toForm().copy(sku = "taken", categoryMode = CategoryMode.NEW, threshold = "1.2345"),
        dark = true,
    )
}
