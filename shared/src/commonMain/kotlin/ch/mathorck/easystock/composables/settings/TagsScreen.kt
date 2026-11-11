@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.LocationIcon
import ch.mathorck.easystock.composables.article.ArticleDetailScreen
import ch.mathorck.easystock.composables.article.ArticleFormDialog
import ch.mathorck.easystock.composables.attributes.ATTRIBUTE_SUMMARY_SEPARATOR
import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.composables.location.LocationContentsScreen
import ch.mathorck.easystock.composables.nfc.TagMoveDialog
import ch.mathorck.easystock.composables.nfc.TagPickerSheet
import ch.mathorck.easystock.composables.nfc.UnknownTagActions
import ch.mathorck.easystock.data.db.TagTarget
import ch.mathorck.easystock.data.db.asTagStore
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.images.rememberArticleImageStore
import ch.mathorck.easystock.nfc.NfcTagListener
import ch.mathorck.easystock.nfc.TagPriority
import ch.mathorck.easystock.nfc.formatTagId
import ch.mathorck.easystock.viewmodels.LocationListViewModel
import ch.mathorck.easystock.viewmodels.NewArticleViewModel
import ch.mathorck.easystock.viewmodels.TagListFilter
import ch.mathorck.easystock.viewmodels.TagListMessage
import ch.mathorck.easystock.viewmodels.TagListSort
import ch.mathorck.easystock.viewmodels.TagListUiState
import ch.mathorck.easystock.viewmodels.TagListViewModel
import ch.mathorck.easystock.viewmodels.TagPickerType
import ch.mathorck.easystock.viewmodels.TagRow
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.ic_more_vert
import easystock.shared.generated.resources.ic_nfc
import easystock.shared.generated.resources.ic_search
import easystock.shared.generated.resources.ic_sort
import easystock.shared.generated.resources.ic_stock
import easystock.shared.generated.resources.nfc_cancel
import easystock.shared.generated.resources.nfc_remove_failed
import easystock.shared.generated.resources.nfc_remove_location_message
import easystock.shared.generated.resources.nfc_remove_message
import easystock.shared.generated.resources.nfc_sheet_read_failed
import easystock.shared.generated.resources.nfc_tags_actions
import easystock.shared.generated.resources.nfc_tags_archived
import easystock.shared.generated.resources.nfc_tags_count
import easystock.shared.generated.resources.nfc_tags_count_articles
import easystock.shared.generated.resources.nfc_tags_count_format
import easystock.shared.generated.resources.nfc_tags_count_locations
import easystock.shared.generated.resources.nfc_tags_empty_hint
import easystock.shared.generated.resources.nfc_tags_empty_title
import easystock.shared.generated.resources.nfc_tags_filter_all
import easystock.shared.generated.resources.nfc_tags_filter_articles
import easystock.shared.generated.resources.nfc_tags_filter_locations
import easystock.shared.generated.resources.nfc_tags_lookup_failed
import easystock.shared.generated.resources.nfc_tags_move_to_article
import easystock.shared.generated.resources.nfc_tags_move_to_location
import easystock.shared.generated.resources.nfc_tags_no_results
import easystock.shared.generated.resources.nfc_tags_no_results_filter
import easystock.shared.generated.resources.nfc_tags_open
import easystock.shared.generated.resources.nfc_tags_scan_hint
import easystock.shared.generated.resources.nfc_tags_search
import easystock.shared.generated.resources.nfc_tags_sort
import easystock.shared.generated.resources.nfc_tags_sort_name
import easystock.shared.generated.resources.nfc_tags_sort_tag
import easystock.shared.generated.resources.nfc_tags_title
import easystock.shared.generated.resources.nfc_tags_type_article
import easystock.shared.generated.resources.nfc_tags_type_location
import easystock.shared.generated.resources.nfc_tags_unknown_message
import easystock.shared.generated.resources.nfc_tags_unknown_title
import easystock.shared.generated.resources.nfc_tags_unlink
import easystock.shared.generated.resources.nfc_tags_unlink_title
import easystock.shared.generated.resources.nfc_tags_unlinked
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How long a scanned row stays highlighted. */
private const val HIGHLIGHT_MILLIS = 4_000L

/**
 * Settings > NFC tags: every tag held by an article or a location. A row opens its article or location;
 * its menu unlinks the tag or moves it elsewhere. A tag scanned here scrolls to its row (this screen takes
 * the reads instead of the Scan page); an unknown one can be linked, or used for a new article or location.
 */
@Composable
fun TagsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val viewModel = viewModel(key = "settings-tags") {
        val attributes = db.attributeDao()
        TagListViewModel(
            db.tagDao().asTagStore(),
            catalog = combine(attributes.observeDefinitions(), attributes.observeActiveLinks(), ::AttributeCatalog),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Above the early returns: the scroll position survives opening a row
    val listState = rememberLazyListState()

    // The forms creating an article or a location with an unknown tag. Keyed: not the Scan page's instances
    val images = rememberArticleImageStore()
    val newArticle = viewModel(key = "tags-new-article") {
        NewArticleViewModel(db.articleFormDao(), db.categoryDao(), db.attributeDao(), images)
    }
    val newArticleState by newArticle.state.collectAsStateWithLifecycle()
    val createdArticle by newArticle.created.collectAsStateWithLifecycle()
    val newLocation = viewModel(key = "tags-new-location") { LocationListViewModel(db.locationDao()) }
    val newLocationState by newLocation.state.collectAsStateWithLifecycle()
    val createdLocation by newLocation.created.collectAsStateWithLifecycle()
    /** The unknown tag given to the open create form, highlighted once created. */
    var creatingTag by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(createdArticle) {
        if (createdArticle != null) {
            creatingTag?.let(viewModel::highlightTag)
            creatingTag = null
            newArticle.consumeCreated()
        }
    }
    LaunchedEffect(createdLocation) {
        if (createdLocation != null) {
            creatingTag?.let(viewModel::highlightTag)
            creatingTag = null
            newLocation.consumeCreated()
        }
    }

    var openArticle by rememberSaveable { mutableStateOf<String?>(null) }
    var openLocation by rememberSaveable { mutableStateOf<String?>(null) }
    openArticle?.let { id ->
        ArticleDetailScreen(articleId = id, onBack = { openArticle = null }, modifier = modifier)
        return
    }
    openLocation?.let { id ->
        LocationContentsScreen(locationId = id, onBack = { openLocation = null }, modifier = modifier)
        return
    }

    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
    var menuFor by remember { mutableStateOf<String?>(null) }
    val locationDialog = newLocationState.dialog
    // Lookup priority: a link sheet (Link) still wins, and the Scan page is not composed while Settings is shown
    NfcTagListener(
        TagPriority.Lookup,
        enabled = state.acceptsTags && menuFor == null && newArticleState == null && locationDialog == null,
        onEvent = viewModel::onTag,
    )

    val highlight = state.highlight
    LaunchedEffect(highlight) {
        if (highlight == null) return@LaunchedEffect
        // The row shows up once the database emits it (just linked or created)
        val index = snapshotFlow { state.rows.indexOfFirst { it.tagId == highlight.tagId } }.first { it >= 0 }
        listState.animateScrollToItem(index)
        delay(HIGHLIGHT_MILLIS)
        viewModel.clearHighlight(highlight.seq)
    }

    TagsContent(
        state = state,
        listState = listState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSortChange = viewModel::onSortChange,
        onOpen = { row ->
            when (val target = row.target) {
                is TagTarget.OfArticle -> openArticle = target.id
                is TagTarget.OfLocation -> openLocation = target.id
            }
        },
        menuFor = menuFor,
        onMenuChange = { menuFor = it },
        onUnlink = viewModel::requestUnlink,
        onReassign = viewModel::startReassign,
        onMessageShown = viewModel::consumeMessage,
        modifier = modifier,
    )

    state.unknownTagId?.let { tagId ->
        UnknownTagSheet(
            tagId = tagId,
            onLinkArticle = { viewModel.linkUnknown(TagPickerType.ARTICLE) },
            onCreateArticle = {
                creatingTag = tagId
                viewModel.dismissUnknown()
                newArticle.start(tagId)
            },
            onLinkLocation = { viewModel.linkUnknown(TagPickerType.LOCATION) },
            onCreateLocation = {
                creatingTag = tagId
                viewModel.dismissUnknown()
                newLocation.startCreate(tagId = tagId)
            },
            onDismiss = viewModel::dismissUnknown,
        )
    }
    state.picker?.let { picker ->
        TagPickerSheet(
            state = picker,
            onQueryChange = viewModel::onPickerQuery,
            onPick = viewModel::pick,
            onDismiss = viewModel::closePicker,
        )
    }
    state.moveRequest?.let { request ->
        TagMoveDialog(request, request.targetName, onConfirm = viewModel::confirmMove, onDismiss = viewModel::cancelMove)
    }
    state.pendingUnlink?.let { row ->
        UnlinkDialog(row, onConfirm = viewModel::confirmUnlink, onDismiss = viewModel::cancelUnlink)
    }
    newArticleState?.let { form ->
        ArticleFormDialog(
            state = form,
            onFormChange = newArticle::update,
            onImageImported = newArticle::onImageImported,
            onDismiss = {
                creatingTag = null
                newArticle.cancel()
            },
            onSave = newArticle::save,
        )
    }
    locationDialog?.let { dialog ->
        LocationDialog(
            state = dialog,
            onFormChange = newLocation::updateForm,
            onDismiss = {
                creatingTag = null
                newLocation.cancel()
            },
            onSave = newLocation::save,
        )
    }
}

/** Stateless list of the tags, previewable. [menuFor] is the tag whose row menu is open. */
@Composable
fun TagsContent(
    state: TagListUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (TagListFilter) -> Unit,
    onSortChange: (TagListSort) -> Unit,
    onOpen: (TagRow) -> Unit,
    onUnlink: (TagRow) -> Unit,
    onReassign: (TagRow, TagPickerType) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    menuFor: String? = null,
    onMenuChange: (String?) -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.text()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }
    Box(modifier.fillMaxSize()) {
        SettingsSubpage(
            title = stringResource(Res.string.nfc_tags_title),
            onBack = onBack,
            actions = { if (state.all.size > 1) SortMenu(state.sort, onSortChange) },
        ) {
            when {
                state.loading -> Unit
                state.all.isEmpty() -> EmptyTags()
                else -> {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(Res.string.nfc_tags_search)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        TagListFilter.entries.forEach { filter ->
                            val selected = state.filter == filter
                            FilterChip(
                                selected = selected,
                                onClick = { onFilterChange(filter) },
                                label = { Text(stringResource(filter.label())) },
                                leadingIcon = if (selected) {
                                    { Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(FilterChipDefaults.IconSize)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    CountHeader(state)
                    if (state.rows.isEmpty()) {
                        val query = state.query.trim()
                        EmptyListHint(
                            if (query.isEmpty()) stringResource(Res.string.nfc_tags_no_results_filter)
                            else stringResource(Res.string.nfc_tags_no_results, query),
                        )
                    } else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                            items(state.rows, key = { it.tagId }) { row ->
                                TagListRow(
                                    row = row,
                                    highlighted = state.highlight?.tagId == row.tagId,
                                    menuOpen = menuFor == row.tagId,
                                    onMenuChange = { open -> onMenuChange(if (open) row.tagId else null) },
                                    onOpen = { onOpen(row) },
                                    onUnlink = { onUnlink(row) },
                                    onReassign = { type -> onReassign(row, type) },
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** "3 tags: 2 articles, 1 location", and how to find a tag by scanning it. */
@Composable
private fun CountHeader(state: TagListUiState) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        Text(
            stringResource(
                Res.string.nfc_tags_count_format,
                pluralStringResource(Res.plurals.nfc_tags_count, state.all.size, state.all.size),
                pluralStringResource(Res.plurals.nfc_tags_count_articles, state.articleCount, state.articleCount),
                pluralStringResource(Res.plurals.nfc_tags_count_locations, state.locationCount, state.locationCount),
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.nfc_tags_scan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagListRow(
    row: TagRow,
    highlighted: Boolean,
    menuOpen: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onUnlink: () -> Unit,
    onReassign: (TagPickerType) -> Unit,
) {
    val container by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
    )
    val archived = stringResource(Res.string.nfc_tags_archived)
    val supporting = listOfNotNull(row.subtitle, archived.takeIf { row.archived }).joinToString(ATTRIBUTE_SUMMARY_SEPARATOR)
    ListItem(
        overlineContent = {
            Text(formatTagId(row.tagId), fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting.takeIf { it.isNotEmpty() }?.let { text ->
            { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            if (row.isLocation) {
                LocationIcon(row.icon, row.color, contentDescription = stringResource(Res.string.nfc_tags_type_location))
            } else {
                Icon(painterResource(Res.drawable.ic_stock), contentDescription = stringResource(Res.string.nfc_tags_type_article))
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { onMenuChange(true) }) {
                    Icon(painterResource(Res.drawable.ic_more_vert), contentDescription = stringResource(Res.string.nfc_tags_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuChange(false) }) {
                    fun pick(action: () -> Unit) {
                        onMenuChange(false)
                        action()
                    }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.nfc_tags_open)) }, onClick = { pick(onOpen) })
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.nfc_tags_move_to_article)) },
                        onClick = { pick { onReassign(TagPickerType.ARTICLE) } },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.nfc_tags_move_to_location)) },
                        onClick = { pick { onReassign(TagPickerType.LOCATION) } },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.nfc_tags_unlink), color = MaterialTheme.colorScheme.error) },
                        onClick = { pick(onUnlink) },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = container),
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

@Composable
private fun SortMenu(sort: TagListSort, onSortChange: (TagListSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_sort), contentDescription = stringResource(Res.string.nfc_tags_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TagListSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (option == TagListSort.NAME) Res.string.nfc_tags_sort_name else Res.string.nfc_tags_sort_tag)) },
                    leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                    onClick = {
                        expanded = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}

/** No tag at all: how to link one. */
@Composable
private fun EmptyTags() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Icon(
            painterResource(Res.drawable.ic_nfc),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(Res.string.nfc_tags_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(Res.string.nfc_tags_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A scanned tag nobody holds: link it, or create an article or a location with it. */
@Composable
private fun UnknownTagSheet(
    tagId: String,
    onLinkArticle: () -> Unit,
    onCreateArticle: () -> Unit,
    onLinkLocation: () -> Unit,
    onCreateLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            Text(stringResource(Res.string.nfc_tags_unknown_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.nfc_tags_unknown_message, formatTagId(tagId)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            UnknownTagActions(
                onLinkArticle = onLinkArticle,
                onCreateArticle = onCreateArticle,
                onLinkLocation = onLinkLocation,
                onCreateLocation = onCreateLocation,
                onCancel = onDismiss,
            )
        }
    }
}

/** Asks before clearing the tag of [row]. The article or location itself stays. */
@Composable
private fun UnlinkDialog(row: TagRow, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.nfc_tags_unlink_title, formatTagId(row.tagId))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(if (row.isLocation) Res.string.nfc_remove_location_message else Res.string.nfc_remove_message))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.nfc_tags_unlink), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.nfc_cancel)) } },
    )
}

private fun TagListFilter.label() = when (this) {
    TagListFilter.ALL -> Res.string.nfc_tags_filter_all
    TagListFilter.ARTICLES -> Res.string.nfc_tags_filter_articles
    TagListFilter.LOCATIONS -> Res.string.nfc_tags_filter_locations
}

private fun TagListMessage.text() = when (this) {
    TagListMessage.READ_FAILED -> Res.string.nfc_sheet_read_failed
    TagListMessage.LOOKUP_FAILED -> Res.string.nfc_tags_lookup_failed
    TagListMessage.UNLINK_FAILED -> Res.string.nfc_remove_failed
    TagListMessage.UNLINKED -> Res.string.nfc_tags_unlinked
}
