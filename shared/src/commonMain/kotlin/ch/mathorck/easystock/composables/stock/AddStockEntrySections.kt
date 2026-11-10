@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.stock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.viewmodels.AddStockEntryForm
import ch.mathorck.easystock.viewmodels.AddStockEntryUiState
import ch.mathorck.easystock.viewmodels.CategoryMode
import ch.mathorck.easystock.viewmodels.ChoiceMode
import ch.mathorck.easystock.viewmodels.FieldError
import ch.mathorck.easystock.viewmodels.matching
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.add_stock_article_create
import easystock.shared.generated.resources.add_stock_article_empty
import easystock.shared.generated.resources.add_stock_article_more
import easystock.shared.generated.resources.add_stock_article_no_match
import easystock.shared.generated.resources.add_stock_article_search
import easystock.shared.generated.resources.add_stock_category
import easystock.shared.generated.resources.add_stock_category_name
import easystock.shared.generated.resources.add_stock_category_new
import easystock.shared.generated.resources.add_stock_category_none
import easystock.shared.generated.resources.add_stock_change
import easystock.shared.generated.resources.add_stock_error_category_deleted
import easystock.shared.generated.resources.add_stock_error_not_positive
import easystock.shared.generated.resources.add_stock_error_quantity
import easystock.shared.generated.resources.add_stock_error_sku_taken
import easystock.shared.generated.resources.add_stock_error_too_long
import easystock.shared.generated.resources.add_stock_location
import easystock.shared.generated.resources.add_stock_location_empty
import easystock.shared.generated.resources.add_stock_location_name
import easystock.shared.generated.resources.add_stock_location_parent
import easystock.shared.generated.resources.add_stock_location_top_level
import easystock.shared.generated.resources.add_stock_mode_existing
import easystock.shared.generated.resources.add_stock_mode_new
import easystock.shared.generated.resources.add_stock_name
import easystock.shared.generated.resources.add_stock_reason_inventory
import easystock.shared.generated.resources.add_stock_reason_purchase
import easystock.shared.generated.resources.add_stock_reason_return
import easystock.shared.generated.resources.add_stock_section_article
import easystock.shared.generated.resources.add_stock_section_location
import easystock.shared.generated.resources.add_stock_sku
import easystock.shared.generated.resources.add_stock_threshold
import easystock.shared.generated.resources.add_stock_threshold_hint
import easystock.shared.generated.resources.add_stock_unit
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.ic_search
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_SEARCH_RESULTS = 5

private typealias FormChange = ((AddStockEntryForm) -> AddStockEntryForm) -> Unit

// ---------- Article ----------

@Composable
internal fun ArticleSection(state: AddStockEntryUiState, onFormChange: FormChange) {
    val form = state.form
    SectionTitle(stringResource(Res.string.add_stock_section_article))
    ChoiceRow(
        options = ChoiceMode.entries,
        selected = form.articleMode,
        label = { stringResource(it.label) },
        onSelect = { mode -> onFormChange { it.copy(articleMode = mode) } },
    )
    when (form.articleMode) {
        ChoiceMode.EXISTING -> ExistingArticle(state, onFormChange)
        ChoiceMode.NEW -> NewArticle(state, onFormChange)
    }
}

@Composable
private fun ExistingArticle(state: AddStockEntryUiState, onFormChange: FormChange) {
    val form = state.form
    val selected = state.articles.firstOrNull { it.id == form.articleId }
    if (selected != null) {
        ArticleRow(
            article = selected,
            selected = true,
            trailing = {
                TextButton(onClick = { onFormChange { it.copy(articleId = null) } }) {
                    Text(stringResource(Res.string.add_stock_change))
                }
            },
        )
        return
    }
    if (state.articles.isEmpty()) {
        if (!state.loading) Hint(stringResource(Res.string.add_stock_article_empty))
        return
    }
    OutlinedTextField(
        value = form.articleQuery,
        onValueChange = { v -> onFormChange { it.copy(articleQuery = v) } },
        placeholder = { Text(stringResource(Res.string.add_stock_article_search)) },
        leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val matches = state.articles.matching(form.articleQuery)
    val query = form.articleQuery.trim()
    if (matches.isEmpty()) {
        Hint(stringResource(Res.string.add_stock_article_no_match, query))
        TextButton(onClick = { onFormChange { it.copy(articleMode = ChoiceMode.NEW, name = query) } }) {
            Icon(painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(Res.string.add_stock_article_create, query))
        }
        return
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            matches.take(MAX_SEARCH_RESULTS).forEach { article ->
                ArticleRow(
                    article = article,
                    selected = false,
                    modifier = Modifier.clickable { onFormChange { it.copy(articleId = article.id) } },
                )
            }
        }
    }
    if (matches.size > MAX_SEARCH_RESULTS) {
        Hint(stringResource(Res.string.add_stock_article_more, matches.size - MAX_SEARCH_RESULTS))
    }
}

@Composable
private fun ArticleRow(
    article: Article,
    selected: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(article.name) },
        supportingContent = { Text(article.sku) },
        leadingContent = if (selected) {
            { Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    )
}

@Composable
private fun NewArticle(state: AddStockEntryUiState, onFormChange: FormChange) {
    val form = state.form
    val errors = state.errors
    FormField(
        value = form.name,
        onValueChange = { v -> onFormChange { it.copy(name = v) } },
        label = stringResource(Res.string.add_stock_name),
        error = errors.name,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    FormField(
        value = form.sku,
        onValueChange = { v -> onFormChange { it.copy(sku = v) } },
        label = stringResource(Res.string.add_stock_sku),
        error = errors.sku,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
    FormField(
        value = form.unit,
        onValueChange = { v -> onFormChange { it.copy(unit = v) } },
        label = stringResource(Res.string.add_stock_unit),
        error = errors.unit,
    )

    val noneText = stringResource(Res.string.add_stock_category_none)
    val newText = stringResource(Res.string.add_stock_category_new)
    val categoryOptions = listOf(DropdownOption(CATEGORY_NONE, noneText)) +
        state.categories.map { DropdownOption(it.id, it.name) } +
        DropdownOption(CATEGORY_NEW, newText)
    Dropdown(
        label = stringResource(Res.string.add_stock_category),
        value = when (form.categoryMode) {
            CategoryMode.NONE -> noneText
            CategoryMode.NEW -> newText
            CategoryMode.EXISTING -> state.categories.firstOrNull { it.id == form.categoryId }?.name.orEmpty()
        },
        options = categoryOptions,
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
            label = stringResource(Res.string.add_stock_category_name),
            error = errors.categoryName,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }

    FormField(
        value = form.threshold,
        onValueChange = { v -> onFormChange { it.copy(threshold = v) } },
        label = stringResource(Res.string.add_stock_threshold),
        error = errors.threshold,
        suffix = form.unit.trim().ifEmpty { null },
        supporting = stringResource(Res.string.add_stock_threshold_hint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private const val CATEGORY_NONE = "#none"
private const val CATEGORY_NEW = "#new"

// ---------- Location ----------

@Composable
internal fun LocationSection(state: AddStockEntryUiState, onFormChange: FormChange) {
    val form = state.form
    SectionTitle(stringResource(Res.string.add_stock_section_location))
    ChoiceRow(
        options = ChoiceMode.entries,
        selected = form.locationMode,
        label = { stringResource(it.label) },
        onSelect = { mode -> onFormChange { it.copy(locationMode = mode) } },
    )
    val options = state.locations.map { DropdownOption(it.id, it.name, it.depth) }
    when (form.locationMode) {
        ChoiceMode.EXISTING -> if (state.locations.isEmpty()) {
            if (!state.loading) Hint(stringResource(Res.string.add_stock_location_empty))
        } else {
            Dropdown(
                label = stringResource(Res.string.add_stock_location),
                value = state.locations.firstOrNull { it.id == form.locationId }?.path.orEmpty(),
                options = options,
                onSelect = { id -> onFormChange { it.copy(locationId = id) } },
            )
        }
        ChoiceMode.NEW -> {
            FormField(
                value = form.locationName,
                onValueChange = { v -> onFormChange { it.copy(locationName = v) } },
                label = stringResource(Res.string.add_stock_location_name),
                error = state.errors.locationName,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (state.locations.isNotEmpty()) {
                val topLevel = stringResource(Res.string.add_stock_location_top_level)
                Dropdown(
                    label = stringResource(Res.string.add_stock_location_parent),
                    value = state.locations.firstOrNull { it.id == form.locationParentId }?.path ?: topLevel,
                    options = listOf(DropdownOption(LOCATION_TOP_LEVEL, topLevel)) + options,
                    onSelect = { id -> onFormChange { it.copy(locationParentId = id.takeUnless { id == LOCATION_TOP_LEVEL }) } },
                )
            }
        }
    }
}

private const val LOCATION_TOP_LEVEL = "#top"

// ---------- Shared pieces ----------

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Text field that shows [error] below it. A missing required value is not shown as an error, Save stays disabled. */
@Composable
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: FieldError?,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun FieldError.message(): String? = when (this) {
    FieldError.REQUIRED -> null
    FieldError.TOO_LONG -> stringResource(Res.string.add_stock_error_too_long)
    FieldError.DUPLICATE -> stringResource(Res.string.add_stock_error_sku_taken)
    FieldError.DELETED_NAME -> stringResource(Res.string.add_stock_error_category_deleted)
    FieldError.INVALID_QUANTITY -> stringResource(Res.string.add_stock_error_quantity)
    FieldError.NOT_POSITIVE -> stringResource(Res.string.add_stock_error_not_positive)
}

@Composable
internal fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
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

private data class DropdownOption(val key: String, val text: String, val depth: Int = 0)

@Composable
private fun Dropdown(label: String, value: String, options: List<DropdownOption>, onSelect: (String) -> Unit) {
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
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text, modifier = Modifier.padding(start = (option.depth * 16).dp)) },
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

internal val ChoiceMode.label: StringResource
    get() = when (this) {
        ChoiceMode.EXISTING -> Res.string.add_stock_mode_existing
        ChoiceMode.NEW -> Res.string.add_stock_mode_new
    }

internal val MovementReason.label: StringResource
    get() = when (this) {
        MovementReason.PURCHASE -> Res.string.add_stock_reason_purchase
        MovementReason.RETURN -> Res.string.add_stock_reason_return
        MovementReason.INVENTORY -> Res.string.add_stock_reason_inventory
        else -> error("Not an entry reason: $this")
    }
