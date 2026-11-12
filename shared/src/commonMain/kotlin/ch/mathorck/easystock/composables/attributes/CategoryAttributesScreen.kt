package ch.mathorck.easystock.composables.attributes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import easystock.shared.generated.resources.category_template_advanced
import easystock.shared.generated.resources.category_template_summary
import easystock.shared.generated.resources.ic_expand_more
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.CategoryAttributeRow
import ch.mathorck.easystock.viewmodels.CategoryAttributesUiState
import ch.mathorck.easystock.viewmodels.CategoryAttributesViewModel
import ch.mathorck.easystock.viewmodels.NameTemplateState
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.attributes_cancel
import easystock.shared.generated.resources.attributes_category_add_attribute
import easystock.shared.generated.resources.attributes_category_linked_empty
import easystock.shared.generated.resources.attributes_category_move_down
import easystock.shared.generated.resources.attributes_category_move_up
import easystock.shared.generated.resources.attributes_category_no_more
import easystock.shared.generated.resources.attributes_category_none_defined
import easystock.shared.generated.resources.attributes_category_remove
import easystock.shared.generated.resources.attributes_category_required
import easystock.shared.generated.resources.attributes_discard
import easystock.shared.generated.resources.attributes_error_save
import easystock.shared.generated.resources.attributes_save
import easystock.shared.generated.resources.attributes_unsaved_message
import easystock.shared.generated.resources.attributes_unsaved_title
import easystock.shared.generated.resources.category_template_apply_all
import easystock.shared.generated.resources.category_template_error_filter
import easystock.shared.generated.resources.category_template_error_key
import easystock.shared.generated.resources.category_template_error_long
import easystock.shared.generated.resources.category_template_error_syntax
import easystock.shared.generated.resources.category_template_hint
import easystock.shared.generated.resources.category_template_keys
import easystock.shared.generated.resources.category_template_label
import easystock.shared.generated.resources.category_template_no_keys
import easystock.shared.generated.resources.category_template_preview_article
import easystock.shared.generated.resources.category_template_preview_empty
import easystock.shared.generated.resources.category_template_preview_sample
import easystock.shared.generated.resources.category_template_renamed
import easystock.shared.generated.resources.category_template_title
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_arrow_downward
import easystock.shared.generated.resources.ic_arrow_upward
import easystock.shared.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Attributes of one category: which apply, required or not, in which order, and the name template of its
 * articles. Changes are saved with the Save button; leaving with unsaved changes asks first. Goes back when the category gets deleted.
 */
@Composable
fun CategoryAttributesScreen(categoryId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val viewModel = viewModel(key = "settings-category-attributes-$categoryId") {
        CategoryAttributesViewModel(categoryId, db.attributeDao(), db.categoryDao())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = {
        viewModel.discard()
        onBack()
    }
    val back = { if (state.dirty) confirmDiscard = true else leave() }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) leave()
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = back)

    CategoryAttributesContent(
        state = state,
        onBack = back,
        onSave = viewModel::save,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onRequiredChange = viewModel::setRequired,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onTemplateChange = viewModel::updateTemplate,
        onApplyToAllChange = viewModel::setApplyToAll,
        onRenamedSeen = viewModel::consumeRenamed,
        modifier = modifier,
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.attributes_unsaved_title)) },
            text = { Text(stringResource(Res.string.attributes_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    leave()
                }) { Text(stringResource(Res.string.attributes_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.attributes_cancel)) }
            },
        )
    }
}

@Composable
fun CategoryAttributesContent(
    state: CategoryAttributesUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRequiredChange: (String, Boolean) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onTemplateChange: (String) -> Unit = {},
    onApplyToAllChange: (Boolean) -> Unit = {},
    onRenamedSeen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SettingsSubpage(
        title = state.categoryName.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.attributes_save)) }
        },
    ) {
        if (state.loading) return@SettingsSubpage
        if (state.saveFailed) {
            Text(
                stringResource(Res.string.attributes_error_save),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        state.renamedCount?.let { RenamedArticlesMessage(it, onRenamedSeen) }
        LazyColumn {
            if (state.rows.isEmpty()) {
                item(key = "empty") { EmptyListHint(stringResource(Res.string.attributes_category_linked_empty)) }
            }
            itemsIndexed(state.rows, key = { _, row -> row.definition.id }) { index, row ->
                AttributeRow(
                    row = row,
                    isFirst = index == 0,
                    isLast = index == state.rows.lastIndex,
                    onRequiredChange = { onRequiredChange(row.definition.id, it) },
                    onMoveUp = { onMoveUp(row.definition.id) },
                    onMoveDown = { onMoveDown(row.definition.id) },
                    onRemove = { onRemove(row.definition.id) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
            item(key = "add") { AddAttributeButton(state, onAdd) }
            item(key = "template") {
                HorizontalDivider()
                NameTemplateSection(
                    template = state.template,
                    keys = templateKeys(state.rows.map { it.definition.key }),
                    enabled = !state.saving,
                    onTemplateChange = onTemplateChange,
                    onApplyToAllChange = onApplyToAllChange,
                )
            }
        }
    }
}

@Composable
private fun AttributeRow(
    row: CategoryAttributeRow,
    isFirst: Boolean,
    isLast: Boolean,
    onRequiredChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.definition.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(row.definition.dataType.label()) + (row.definition.unit?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(painterResource(Res.drawable.ic_arrow_upward), contentDescription = stringResource(Res.string.attributes_category_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(painterResource(Res.drawable.ic_arrow_downward), contentDescription = stringResource(Res.string.attributes_category_move_down))
            }
            IconButton(onClick = onRemove) {
                Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.attributes_category_remove))
            }
        }
        // Required has no meaning for yes/no attributes: unset means "no"
        if (row.definition.dataType != AttributeDataType.BOOL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.attributes_category_required),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = row.required, onCheckedChange = onRequiredChange)
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun AddAttributeButton(state: CategoryAttributesUiState, onAdd: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        when {
            state.available.isNotEmpty() -> Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Icon(painterResource(Res.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.attributes_category_add_attribute))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.available.forEach { definition ->
                        DropdownMenuItem(
                            text = { Text(definition.label) },
                            onClick = {
                                expanded = false
                                onAdd(definition.id)
                            },
                        )
                    }
                }
            }
            state.rows.isEmpty() -> EmptyHintText(stringResource(Res.string.attributes_category_none_defined))
            else -> EmptyHintText(stringResource(Res.string.attributes_category_no_more))
        }
    }
}

/** "N articles renamed" after a save, with a close button. */
@Composable
internal fun RenamedArticlesMessage(count: Int, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            pluralStringResource(Res.plurals.category_template_renamed, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.attributes_cancel))
        }
    }
}

/** Name template field, its keys (tap to insert at the cursor), the error or a preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameTemplateSection(
    template: NameTemplateState,
    keys: List<String>,
    enabled: Boolean,
    onTemplateChange: (String) -> Unit,
    onApplyToAllChange: (Boolean) -> Unit,
) {
    // Local value for the cursor. The state comes back a frame later: follow it only when it changed from
    // outside (load, save, discard), not when it echoes what was typed.
    var field by remember { mutableStateOf(TextFieldValue(template.text, TextRange(template.text.length))) }
    var lastSent by remember { mutableStateOf(template.text) }
    LaunchedEffect(template.text) {
        if (template.text != lastSent && template.text != field.text) {
            field = TextFieldValue(template.text, TextRange(template.text.length))
            lastSent = template.text
        }
    }
    val send = { text: String ->
        lastSent = text
        onTemplateChange(text)
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.category_template_advanced), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (template.text.isBlank()) stringResource(Res.string.category_template_title)
                    else stringResource(Res.string.category_template_summary, template.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(painterResource(Res.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.rotate(chevronRotation))
        }
        AnimatedVisibility(visible = expanded) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.category_template_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.category_template_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = field,
            onValueChange = { v ->
                val value = if (v.text.length > MAX_NAME_TEMPLATE_LENGTH) field else v
                field = value
                if (value.text != lastSent) send(value.text)
            },
            label = { Text(stringResource(Res.string.category_template_label)) },
            isError = template.error != null,
            supportingText = {
                val error = template.error
                when {
                    error != null -> Text(error.message())
                    template.text.isBlank() -> Unit
                    template.preview.isNullOrEmpty() -> Text(stringResource(Res.string.category_template_preview_empty))
                    template.previewSource == NameTemplateState.PreviewSource.ARTICLE ->
                        Text(stringResource(Res.string.category_template_preview_article, template.preview))
                    else -> Text(stringResource(Res.string.category_template_preview_sample, template.preview))
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.category_template_keys), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { key ->
                AssistChip(
                    onClick = {
                        val (text, cursor) = insertPlaceholder(field.text, field.selection.min, field.selection.max, key)
                        if (text.length <= MAX_NAME_TEMPLATE_LENGTH) {
                            field = TextFieldValue(text, TextRange(cursor))
                            send(text)
                        }
                    },
                    label = { Text("{$key}") },
                    enabled = enabled,
                )
            }
        }
        // Only {category}: no attribute yet
        if (keys.all { it == CATEGORY_TEMPLATE_KEY }) EmptyHintText(stringResource(Res.string.category_template_no_keys))
        if (template.text.isNotBlank() && template.manualNameCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(Res.plurals.category_template_apply_all, template.manualNameCount, template.manualNameCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = template.applyToAll, onCheckedChange = onApplyToAllChange, enabled = enabled)
            }
        }
    }
        }
    }
}

/** Keys offered as chips: the built-in {category} first, then the attributes (an attribute "category" is listed once). */
private fun templateKeys(attributeKeys: List<String>): List<String> = (listOf(CATEGORY_TEMPLATE_KEY) + attributeKeys).distinct()

@Composable
private fun NameTemplateError.message(): String = when (kind) {
    NameTemplateError.Kind.SYNTAX -> stringResource(Res.string.category_template_error_syntax)
    NameTemplateError.Kind.UNKNOWN_KEY -> stringResource(Res.string.category_template_error_key, detail.orEmpty())
    NameTemplateError.Kind.UNKNOWN_FILTER -> stringResource(Res.string.category_template_error_filter, detail.orEmpty())
    NameTemplateError.Kind.TOO_LONG -> stringResource(Res.string.category_template_error_long, MAX_NAME_TEMPLATE_LENGTH)
}

@Composable
private fun EmptyHintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview(showBackground = true)
@Composable
private fun CategoryAttributesContentPreview() {
    fun definition(id: String, label: String, type: AttributeDataType, unit: String? = null) =
        AttributeDefinition(id = id, key = id, label = label, dataType = type, unit = unit, createdAt = 0, updatedAt = 0)
    EasyStockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategoryAttributesContent(
                state = CategoryAttributesUiState(
                    loading = false,
                    categoryName = "Ropes",
                    rows = listOf(
                        CategoryAttributeRow(definition("breaking_strength", "Breaking strength", AttributeDataType.NUMBER, "N"), required = true),
                        CategoryAttributeRow(definition("length", "Length", AttributeDataType.NUMBER, "m"), required = false),
                    ),
                    available = listOf(definition("dynamic", "Dynamic", AttributeDataType.BOOL)),
                    template = NameTemplateState(
                        text = "Rope {length} {breaking_strength}",
                        preview = "Rope 10 m 10 N",
                        manualNameCount = 3,
                    ),
                    dirty = true,
                ),
                onBack = {}, onSave = {}, onAdd = {}, onRemove = {}, onRequiredChange = { _, _ -> }, onMoveUp = {}, onMoveDown = {},
            )
        }
    }
}
