@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.stock

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mathorck.easystock.data.db.STOCK_ENTRY_REASONS
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.AddStockEntryForm
import ch.mathorck.easystock.viewmodels.AddStockEntryUiState
import ch.mathorck.easystock.viewmodels.AddStockEntryViewModel
import ch.mathorck.easystock.viewmodels.CategoryMode
import ch.mathorck.easystock.viewmodels.ChoiceMode
import ch.mathorck.easystock.viewmodels.buildLocationOptions
import ch.mathorck.easystock.viewmodels.validate
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.add_stock_cancel
import easystock.shared.generated.resources.add_stock_comment
import easystock.shared.generated.resources.add_stock_error_save
import easystock.shared.generated.resources.add_stock_quantity
import easystock.shared.generated.resources.add_stock_reason
import easystock.shared.generated.resources.add_stock_save
import easystock.shared.generated.resources.add_stock_section_entry
import easystock.shared.generated.resources.add_stock_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Form to record stock coming in (purchase, return, inventory). Closes itself after a save. */
@Composable
fun AddStockEntrySheet(onDismiss: () -> Unit) {
    val db = rememberAppDatabase()
    val viewModel = viewModel {
        AddStockEntryViewModel(db.articleDao(), db.categoryDao(), db.stockDao(), db.stockEntryDao())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // No partially expanded state: the form is long and needs the keyboard
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    // The ViewModel outlives the sheet: clear the form whenever the sheet closes
    val dismiss = {
        viewModel.reset()
        onDismiss()
    }
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() }
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        AddStockEntryContent(
            state = state,
            onFormChange = viewModel::update,
            onCancel = close,
            onSave = { viewModel.save(onSaved = close) },
        )
    }
}

@Composable
fun AddStockEntryContent(
    state: AddStockEntryUiState,
    onFormChange: ((AddStockEntryForm) -> AddStockEntryForm) -> Unit,
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
        Text(stringResource(Res.string.add_stock_title), style = MaterialTheme.typography.titleLarge)

        ArticleSection(state, onFormChange)
        LocationSection(state, onFormChange)

        SectionTitle(stringResource(Res.string.add_stock_section_entry))
        FormField(
            value = form.quantity,
            onValueChange = { v -> onFormChange { it.copy(quantity = v) } },
            label = stringResource(Res.string.add_stock_quantity),
            error = state.errors.quantity,
            suffix = selectedUnit(state),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(stringResource(Res.string.add_stock_reason), style = MaterialTheme.typography.labelLarge)
        ChoiceRow(
            options = STOCK_ENTRY_REASONS,
            selected = form.reason,
            label = { stringResource(it.label) },
            onSelect = { reason -> onFormChange { it.copy(reason = reason) } },
        )
        FormField(
            value = form.comment,
            onValueChange = { v -> onFormChange { it.copy(comment = v) } },
            label = stringResource(Res.string.add_stock_comment),
            error = null,
            singleLine = false,
        )

        if (state.saveFailed) {
            Text(
                stringResource(Res.string.add_stock_error_save),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.add_stock_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.add_stock_save)) }
        }
    }
}

/** Unit of the chosen or new article, shown next to the quantity. */
private fun selectedUnit(state: AddStockEntryUiState): String? = when (state.form.articleMode) {
    ChoiceMode.NEW -> state.form.unit.trim().ifEmpty { null }
    ChoiceMode.EXISTING -> state.articles.firstOrNull { it.id == state.form.articleId }?.unit
}

// ---------- Previews ----------

private val previewArticles = listOf(
    Article(id = "a1", sku = "BLT-8", name = "Bolt M8", createdAt = 0, updatedAt = 0),
    Article(id = "a2", sku = "CT-200", name = "Cable tie 200 mm", createdAt = 0, updatedAt = 0),
    Article(id = "a3", sku = "TAPE-50", name = "Duct tape", unit = "m", createdAt = 0, updatedAt = 0),
)
private val previewCategories = listOf(Category(id = "c1", name = "Hardware", createdAt = 0, updatedAt = 0))
private val previewLocations = listOf(
    Location(id = "l1", name = "Workshop", createdAt = 0, updatedAt = 0),
    Location(id = "l2", name = "Shelf A", parentId = "l1", createdAt = 0, updatedAt = 0),
    Location(id = "l3", name = "Van", createdAt = 0, updatedAt = 0),
)

private fun previewState(form: AddStockEntryForm) = AddStockEntryUiState(
    loading = false,
    form = form,
    articles = previewArticles,
    categories = previewCategories,
    locations = buildLocationOptions(previewLocations),
    errors = form.validate(previewArticles, previewCategories, previewLocations),
)

private val existingArticleForm = AddStockEntryForm(articleId = "a1", locationId = "l2", quantity = "12,5", comment = "Order #42")
private val newArticleForm = AddStockEntryForm(
    articleMode = ChoiceMode.NEW,
    name = "Bolt M10",
    sku = "BLT-8",
    categoryMode = CategoryMode.EXISTING,
    categoryId = "c1",
    threshold = "20",
    locationMode = ChoiceMode.NEW,
    locationName = "Bin 3",
    locationParentId = "l2",
    quantity = "0",
)

@Composable
private fun AddStockEntryPreview(form: AddStockEntryForm, dark: Boolean) {
    EasyStockTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            AddStockEntryContent(state = previewState(form), onFormChange = {}, onCancel = {}, onSave = {})
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AddStockEntryExistingPreview() = AddStockEntryPreview(existingArticleForm, dark = false)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AddStockEntryExistingDarkPreview() = AddStockEntryPreview(existingArticleForm, dark = true)

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun AddStockEntryNewPreview() = AddStockEntryPreview(newArticleForm, dark = false)

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun AddStockEntryNewDarkPreview() = AddStockEntryPreview(newArticleForm, dark = true)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AddStockEntrySearchPreview() = AddStockEntryPreview(AddStockEntryForm(articleQuery = "b"), dark = false)
