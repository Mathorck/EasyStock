@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.nfc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import ch.mathorck.easystock.nfc.NfcAvailability
import ch.mathorck.easystock.nfc.formatTagId
import easystock.shared.generated.resources.nfc_create_location_with_tag
import easystock.shared.generated.resources.nfc_create_with_tag
import easystock.shared.generated.resources.nfc_link_existing
import easystock.shared.generated.resources.nfc_link_existing_location
import ch.mathorck.easystock.nfc.NfcTagListener
import ch.mathorck.easystock.nfc.TagEvent
import ch.mathorck.easystock.nfc.TagPriority
import ch.mathorck.easystock.nfc.appNfcTags
import ch.mathorck.easystock.nfc.rememberOpenNfcSettings
import ch.mathorck.easystock.viewmodels.TagLinkUiState
import ch.mathorck.easystock.composables.attributes.ATTRIBUTE_SUMMARY_SEPARATOR
import ch.mathorck.easystock.viewmodels.TagPickerUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_link
import easystock.shared.generated.resources.ic_nfc
import easystock.shared.generated.resources.nfc_cancel
import easystock.shared.generated.resources.nfc_error_save
import easystock.shared.generated.resources.nfc_link_tag
import easystock.shared.generated.resources.nfc_move_confirm
import easystock.shared.generated.resources.nfc_move_article_to_article
import easystock.shared.generated.resources.nfc_move_article_to_location
import easystock.shared.generated.resources.nfc_move_location_to_article
import easystock.shared.generated.resources.nfc_move_location_to_location
import easystock.shared.generated.resources.nfc_location_tag_hint
import easystock.shared.generated.resources.nfc_picker_location_empty
import easystock.shared.generated.resources.nfc_picker_location_hint
import easystock.shared.generated.resources.nfc_picker_location_no_results
import easystock.shared.generated.resources.nfc_picker_location_search
import easystock.shared.generated.resources.nfc_picker_location_title
import easystock.shared.generated.resources.nfc_remove_location_message
import easystock.shared.generated.resources.nfc_sheet_replace_location_hint
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
import ch.mathorck.easystock.data.db.TagOwner
import ch.mathorck.easystock.data.db.TagTarget
import ch.mathorck.easystock.viewmodels.TagMoveRequest
import ch.mathorck.easystock.viewmodels.TagPickerType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Asks before moving a tag from its owner (article or location) to [TagMoveRequest.target], named [targetName]. */
@Composable
fun TagMoveDialog(request: TagMoveRequest, targetName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val toLocation = request.target is TagTarget.OfLocation
    val message = when (request.owner) {
        is TagOwner.OfArticle -> if (toLocation) Res.string.nfc_move_article_to_location else Res.string.nfc_move_article_to_article
        is TagOwner.OfLocation -> if (toLocation) Res.string.nfc_move_location_to_location else Res.string.nfc_move_location_to_article
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.nfc_move_title)) },
        text = { Text(stringResource(message, request.owner.name, targetName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.nfc_move_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.nfc_cancel)) } },
    )
}

/** Searchable list of the articles (or locations) without a tag, to link an unknown tag to one of them. */
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
            val forLocation = state.type == TagPickerType.LOCATION
            Text(
                stringResource(if (forLocation) Res.string.nfc_picker_location_title else Res.string.nfc_picker_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(if (forLocation) Res.string.nfc_picker_location_hint else Res.string.nfc_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.noneUntagged) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(if (forLocation) Res.string.nfc_picker_location_search else Res.string.nfc_picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.saveFailed) ErrorText(stringResource(Res.string.nfc_error_save))
        }
        val forLocation = state.type == TagPickerType.LOCATION
        val count = if (forLocation) state.locations?.size else state.articles?.size
        when {
            state.loading || state.saving -> Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            ) { CircularProgressIndicator() }
            state.noneUntagged -> SheetHint(
                stringResource(if (forLocation) Res.string.nfc_picker_location_empty else Res.string.nfc_picker_empty),
            )
            count == 0 -> SheetHint(
                stringResource(
                    if (forLocation) Res.string.nfc_picker_location_no_results else Res.string.nfc_picker_no_results,
                    state.query.trim(),
                ),
            )
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                if (forLocation) {
                    // Indented as a tree; while searching, the matches are scattered, so the full path is shown instead.
                    val searching = state.query.isNotBlank()
                    items(state.locations.orEmpty(), key = { it.id }) { location ->
                        PickerRow(
                            location.name,
                            location.path.takeIf { searching && it != location.name },
                            onClick = { onPick(location.id) },
                            indent = if (searching) 0 else location.depth,
                        )
                    }
                } else {
                    items(state.articles.orEmpty(), key = { it.id }) { article ->
                        PickerRow(article.name, articleSubtitle(article.sku, state.attributeLines[article.id]), onClick = { onPick(article.id) })
                    }
                }
            }
        }
    }
}

/** The SKU and the properties of an article, joined, to tell same-name articles apart. Null when it has neither. */
internal fun articleSubtitle(sku: String?, attributes: String?): String? =
    listOfNotNull(sku?.takeIf { it.isNotBlank() }, attributes?.takeIf { it.isNotBlank() }).joinToString(ATTRIBUTE_SUMMARY_SEPARATOR).ifEmpty { null }

@Composable
private fun PickerRow(title: String, subtitle: String?, onClick: () -> Unit, indent: Int = 0) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { text -> { Text(text) } },
        leadingContent = { Icon(painterResource(Res.drawable.ic_link), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick).padding(start = (indent * 24).dp),
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
 * The NFC tag of an article (or a location, [forLocation]): the tag ID or "no tag", and the link, replace
 * and remove actions. Removing asks first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleTagSection(
    tagId: String?,
    removeFailed: Boolean,
    onLink: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    forLocation: Boolean = false,
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
                        tagId?.let(::formatTagId) ?: stringResource(Res.string.nfc_tag_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (tagId != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLink) {
                    Text(stringResource(if (tagId == null) Res.string.nfc_link_tag else Res.string.nfc_replace_tag), maxLines = 1)
                }
                if (tagId != null) {
                    TextButton(onClick = { confirmRemove = true }) { Text(stringResource(Res.string.nfc_remove_tag), maxLines = 1) }
                }
            }
            if (removeFailed) ErrorText(stringResource(Res.string.nfc_remove_failed))
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(Res.string.nfc_remove_title)) },
            text = { Text(stringResource(if (forLocation) Res.string.nfc_remove_location_message else Res.string.nfc_remove_message)) },
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
 * Waits for a tag to link to the article or location named [targetName]. While shown, the tags read go
 * here, not to the Scan lookup. [currentTagId] is the tag being replaced, if any.
 */
@Composable
fun TagLinkSheet(
    state: TagLinkUiState,
    currentTagId: String?,
    targetName: String,
    onTag: (TagEvent) -> Unit,
    onConfirmMove: () -> Unit,
    onCancelMove: () -> Unit,
    onDismiss: () -> Unit,
    forLocation: Boolean = false,
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
            if (forLocation) CenteredText(stringResource(Res.string.nfc_location_tag_hint))
            currentTagId?.let {
                CenteredText(stringResource(if (forLocation) Res.string.nfc_sheet_replace_location_hint else Res.string.nfc_sheet_replace_hint, formatTagId(it)))
            }
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
    state.moveRequest?.let { TagMoveDialog(it, targetName, onConfirm = onConfirmMove, onDismiss = onCancelMove) }
}

/** What to do with a tag nobody holds: link it to an untagged article or location, or create one with it. */
@Composable
fun UnknownTagActions(
    onLinkArticle: () -> Unit,
    onCreateArticle: () -> Unit,
    onLinkLocation: () -> Unit,
    onCreateLocation: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onLinkArticle, modifier = Modifier.widthIn(min = 280.dp)) {
            Text(stringResource(Res.string.nfc_link_existing))
        }
        OutlinedButton(onClick = onCreateArticle, modifier = Modifier.widthIn(min = 280.dp)) {
            Text(stringResource(Res.string.nfc_create_with_tag))
        }
        Button(onClick = onLinkLocation, modifier = Modifier.widthIn(min = 280.dp)) {
            Text(stringResource(Res.string.nfc_link_existing_location))
        }
        OutlinedButton(onClick = onCreateLocation, modifier = Modifier.widthIn(min = 280.dp)) {
            Text(stringResource(Res.string.nfc_create_location_with_tag))
        }
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.nfc_cancel)) }
    }
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
