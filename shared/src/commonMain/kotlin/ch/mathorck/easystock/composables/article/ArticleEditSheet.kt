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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
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
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.ArticleEditForm
import ch.mathorck.easystock.viewmodels.ArticleEditUiState
import ch.mathorck.easystock.viewmodels.CategoryMode
import ch.mathorck.easystock.viewmodels.FieldError
import ch.mathorck.easystock.viewmodels.toEditForm
import ch.mathorck.easystock.viewmodels.validate
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.article_cancel
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
import easystock.shared.generated.resources.article_name
import easystock.shared.generated.resources.article_save
import easystock.shared.generated.resources.article_sku
import easystock.shared.generated.resources.article_stock_hint
import easystock.shared.generated.resources.article_threshold
import easystock.shared.generated.resources.article_threshold_hint
import easystock.shared.generated.resources.article_unit
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private typealias EditChange = ((ArticleEditForm) -> ArticleEditForm) -> Unit

/** Edit form of an article. The stock is not editable here. [onDismiss] closes the form without saving. */
@Composable
fun ArticleEditSheet(
    state: ArticleEditUiState,
    onFormChange: EditChange,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    // No partially expanded state: the form needs the keyboard
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ArticleEditContent(state = state, onFormChange = onFormChange, onCancel = close, onSave = onSave)
    }
}

@Composable
fun ArticleEditContent(
    state: ArticleEditUiState,
    onFormChange: EditChange,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val errors = state.errors
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(Res.string.article_edit_title), style = MaterialTheme.typography.titleLarge)

        EditField(
            value = form.name,
            onValueChange = { v -> onFormChange { it.copy(name = v) } },
            label = stringResource(Res.string.article_name),
            error = errors.name,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        EditField(
            value = form.sku,
            onValueChange = { v -> onFormChange { it.copy(sku = v) } },
            label = stringResource(Res.string.article_sku),
            error = errors.sku,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        EditField(
            value = form.description,
            onValueChange = { v -> onFormChange { it.copy(description = v) } },
            label = stringResource(Res.string.article_description),
            error = null,
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        EditField(
            value = form.unit,
            onValueChange = { v -> onFormChange { it.copy(unit = v) } },
            label = stringResource(Res.string.article_unit),
            error = errors.unit,
        )

        CategoryField(state, onFormChange)

        EditField(
            value = form.threshold,
            onValueChange = { v -> onFormChange { it.copy(threshold = v) } },
            label = stringResource(Res.string.article_threshold),
            error = errors.threshold,
            suffix = form.unit.trim().ifEmpty { null },
            supporting = stringResource(Res.string.article_threshold_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(
            stringResource(Res.string.article_stock_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.saveFailed) {
            Text(
                stringResource(Res.string.article_error_save),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.article_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.article_save)) }
        }
    }
}

@Composable
private fun CategoryField(state: ArticleEditUiState, onFormChange: EditChange) {
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
        EditField(
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
private fun EditField(
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
        maxLines = if (singleLine) 1 else 4,
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

private fun previewState(form: ArticleEditForm) = ArticleEditUiState(
    form = form,
    categories = previewCategories,
    currentCategoryName = "Hardware",
    errors = form.validate(previewArticle, emptyList(), previewCategories, rejectedSku = "TAKEN"),
)

@Composable
private fun ArticleEditPreview(form: ArticleEditForm, dark: Boolean) {
    EasyStockTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ArticleEditContent(state = previewState(form), onFormChange = {}, onCancel = {}, onSave = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleEditLightPreview() {
    ArticleEditPreview(previewArticle.toEditForm(), dark = false)
}

@Preview(showBackground = true)
@Composable
private fun ArticleEditErrorsPreview() {
    ArticleEditPreview(
        previewArticle.toEditForm().copy(sku = "taken", categoryMode = CategoryMode.NEW, threshold = "1.2345"),
        dark = true,
    )
}
