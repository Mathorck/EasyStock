@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.nfc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.nfc.NfcAvailability
import ch.mathorck.easystock.nfc.NfcTagListener
import ch.mathorck.easystock.nfc.TagEvent
import ch.mathorck.easystock.nfc.TagPriority
import ch.mathorck.easystock.nfc.appNfcTags
import ch.mathorck.easystock.nfc.rememberOpenNfcSettings
import ch.mathorck.easystock.viewmodels.TagLinkUiState
import ch.mathorck.easystock.viewmodels.TagPickerUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_link
import easystock.shared.generated.resources.ic_nfc
import easystock.shared.generated.resources.nfc_cancel
import easystock.shared.generated.resources.nfc_error_save
import easystock.shared.generated.resources.nfc_link_tag
import easystock.shared.generated.resources.nfc_move_confirm
import easystock.shared.generated.resources.nfc_move_message
import easystock.shared.generated.resources.nfc_move_title
import easystock.shared.generated.resources.nfc_open_settings
import easystock.shared.generated.resources.nfc_picker_empty
import easystock.shared.generated.resources.nfc_picker_hint
import easystock.shared.generated.resources.nfc_picker_no_results
import easystock.shared.generated.resources.nfc_picker_search
import easystock.shared.generated.resources.nfc_picker_title
import easystock.shared.generated.resources.nfc_remove_confirm
import easystock.shared.generated.resources.nfc_remove_failed
import easystock.shared.generated.resources.nfc_remove_message
import easystock.shared.generated.resources.nfc_remove_tag
import easystock.shared.generated.resources.nfc_remove_title
import easystock.shared.generated.resources.nfc_replace_tag
import easystock.shared.generated.resources.nfc_sheet_disabled
import easystock.shared.generated.resources.nfc_sheet_hint
import easystock.shared.generated.resources.nfc_sheet_link_title
import easystock.shared.generated.resources.nfc_sheet_read_failed
import easystock.shared.generated.resources.nfc_sheet_replace_hint
import easystock.shared.generated.resources.nfc_sheet_replace_title
import easystock.shared.generated.resources.nfc_sheet_unavailable
import easystock.shared.generated.resources.nfc_tag_label
import easystock.shared.generated.resources.nfc_tag_none
import ch.mathorck.easystock.data.model.Article
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Asks before moving a tag from [ownerName] to another article. */
@Composable
fun TagMoveDialog(ownerName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.nfc_move_title)) },
        text = { Text(stringResource(Res.string.nfc_move_message, ownerName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.nfc_move_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.nfc_cancel)) } },
    )
}

/** Searchable list of the articles without a tag, to link an unknown tag to one of them. */
@Composable
fun TagPickerSheet(
    state: TagPickerUiState,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.nfc_picker_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.nfc_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.noneUntagged) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(Res.string.nfc_picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.saveFailed) ErrorText(stringResource(Res.string.nfc_error_save))
        }
        val articles = state.articles
        when {
            articles == null || state.saving -> Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            ) { CircularProgressIndicator() }
            state.noneUntagged -> SheetHint(stringResource(Res.string.nfc_picker_empty))
            articles.isEmpty() -> SheetHint(stringResource(Res.string.nfc_picker_no_results, state.query.trim()))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                items(articles, key = { it.id }) { article ->
                    PickerRow(article, onClick = { onPick(article.id) })
                }
            }
        }
    }
}

@Composable
private fun PickerRow(article: Article, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(article.name) },
        supportingContent = article.sku?.takeIf { it.isNotBlank() }?.let { sku -> { Text(sku) } },
        leadingContent = { Icon(painterResource(Res.drawable.ic_link), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SheetHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/**
 * The NFC tag of an article on its detail screen: the tag ID or "no tag", and the link, replace and remove actions.
 * Removing asks first.
 */
@Composable
fun ArticleTagSection(
    tagId: String?,
    removeFailed: Boolean,
    onLink: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_nfc),
                    contentDescription = null,
                    tint = if (tagId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.nfc_tag_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        tagId ?: stringResource(Res.string.nfc_tag_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (tagId != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLink) {
                    Text(stringResource(if (tagId == null) Res.string.nfc_link_tag else Res.string.nfc_replace_tag))
                }
                if (tagId != null) {
                    TextButton(onClick = { confirmRemove = true }) { Text(stringResource(Res.string.nfc_remove_tag)) }
                }
            }
            if (removeFailed) ErrorText(stringResource(Res.string.nfc_remove_failed))
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(Res.string.nfc_remove_title)) },
            text = { Text(stringResource(Res.string.nfc_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(Res.string.nfc_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(Res.string.nfc_cancel)) } },
        )
    }
}

/**
 * Waits for a tag to link to the article. While shown, the tags read go here, not to the Scan lookup.
 * [currentTagId] is the tag being replaced, if any.
 */
@Composable
fun TagLinkSheet(
    state: TagLinkUiState,
    currentTagId: String?,
    onTag: (TagEvent) -> Unit,
    onConfirmMove: () -> Unit,
    onCancelMove: () -> Unit,
    onDismiss: () -> Unit,
) {
    NfcTagListener(TagPriority.Link, enabled = state.acceptsTags, onEvent = onTag)
    val availability by appNfcTags.availability.collectAsStateWithLifecycle()
    val openSettings = rememberOpenNfcSettings()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(if (currentTagId == null) Res.string.nfc_sheet_link_title else Res.string.nfc_sheet_replace_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.saving) {
                CircularProgressIndicator(Modifier.size(48.dp))
            } else {
                Icon(
                    painterResource(Res.drawable.ic_nfc),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            CenteredText(stringResource(Res.string.nfc_sheet_hint))
            currentTagId?.let { CenteredText(stringResource(Res.string.nfc_sheet_replace_hint, it)) }
            when (availability) {
                NfcAvailability.Disabled -> {
                    CenteredText(stringResource(Res.string.nfc_sheet_disabled), error = true)
                    OutlinedButton(onClick = openSettings) { Text(stringResource(Res.string.nfc_open_settings)) }
                }
                NfcAvailability.Unavailable -> CenteredText(stringResource(Res.string.nfc_sheet_unavailable), error = true)
                NfcAvailability.Enabled, NfcAvailability.Unknown -> Unit
            }
            if (state.readFailed) CenteredText(stringResource(Res.string.nfc_sheet_read_failed), error = true)
            if (state.saveFailed) CenteredText(stringResource(Res.string.nfc_error_save), error = true)
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.nfc_cancel)) }
        }
    }
    state.moveRequest?.let { TagMoveDialog(it.owner.name, onConfirm = onConfirmMove, onDismiss = onCancelMove) }
}

@Composable
private fun CenteredText(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
