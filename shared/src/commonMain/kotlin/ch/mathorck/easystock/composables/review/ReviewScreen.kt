package ch.mathorck.easystock.composables.review

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.article.ArticleDetailScreen
import ch.mathorck.easystock.composables.article.ArticleFormDialog
import ch.mathorck.easystock.composables.article.label
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.ReviewResult
import ch.mathorck.easystock.images.rememberArticleImageStore
import ch.mathorck.easystock.nfc.NfcTagListener
import ch.mathorck.easystock.nfc.TagPriority
import ch.mathorck.easystock.review.LineFilter
import ch.mathorck.easystock.review.LineStatus
import ch.mathorck.easystock.review.isAddedUnit
import ch.mathorck.easystock.review.isSerial
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.NewArticleViewModel
import ch.mathorck.easystock.viewmodels.ReviewLineUi
import ch.mathorck.easystock.viewmodels.ReviewMessage
import ch.mathorck.easystock.viewmodels.ReviewUiState
import ch.mathorck.easystock.viewmodels.ReviewViewModel
import ch.mathorck.easystock.viewmodels.formatDateTime
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.ic_flag
import easystock.shared.generated.resources.ic_more_vert
import easystock.shared.generated.resources.ic_remove
import easystock.shared.generated.resources.ic_warning
import easystock.shared.generated.resources.review_add_found
import easystock.shared.generated.resources.review_added_unit
import easystock.shared.generated.resources.review_article_deleted
import easystock.shared.generated.resources.review_cancel_message
import easystock.shared.generated.resources.review_cancel_review
import easystock.shared.generated.resources.review_cancel_title
import easystock.shared.generated.resources.review_comment
import easystock.shared.generated.resources.review_correct
import easystock.shared.generated.resources.review_count
import easystock.shared.generated.resources.review_counted
import easystock.shared.generated.resources.review_decrease
import easystock.shared.generated.resources.review_edit_info
import easystock.shared.generated.resources.review_elsewhere
import easystock.shared.generated.resources.review_elsewhere_title
import easystock.shared.generated.resources.review_empty
import easystock.shared.generated.resources.review_expected
import easystock.shared.generated.resources.review_filter_all
import easystock.shared.generated.resources.review_filter_discrepancies
import easystock.shared.generated.resources.review_filter_empty
import easystock.shared.generated.resources.review_filter_flagged
import easystock.shared.generated.resources.review_filter_unchecked
import easystock.shared.generated.resources.review_finish
import easystock.shared.generated.resources.review_flag
import easystock.shared.generated.resources.review_flag_set
import easystock.shared.generated.resources.review_found_at
import easystock.shared.generated.resources.review_gone
import easystock.shared.generated.resources.review_increase
import easystock.shared.generated.resources.review_keep
import easystock.shared.generated.resources.review_missing
import easystock.shared.generated.resources.review_more
import easystock.shared.generated.resources.review_msg_closed
import easystock.shared.generated.resources.review_msg_failed
import easystock.shared.generated.resources.review_msg_finished
import easystock.shared.generated.resources.review_no_serial
import easystock.shared.generated.resources.review_present
import easystock.shared.generated.resources.review_progress
import easystock.shared.generated.resources.review_progress_discrepancies
import easystock.shared.generated.resources.review_read_only
import easystock.shared.generated.resources.review_reset
import easystock.shared.generated.resources.review_tag_failed
import easystock.shared.generated.resources.review_tag_hint
import easystock.shared.generated.resources.review_tag_not_in_review
import easystock.shared.generated.resources.review_tag_unknown
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Checklist of a stock review: count quantities, mark units, flag info to fix, add found items, then finish.
 * An NFC tag scanned while it is shown jumps to the line of its article. A finished review is read-only.
 */
@Composable
fun ReviewScreen(reviewId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val viewModel = viewModel(key = "review-$reviewId") {
        ReviewViewModel(reviewId, db.reviewDao(), db.attributeDao(), findArticleByTag = db.articleDao()::findByBarcode)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val jumpTo by viewModel.jumpTo.collectAsStateWithLifecycle()
    val openFound by viewModel.openFound.collectAsStateWithLifecycle()
    val images = rememberArticleImageStore()
    val newArticleViewModel = viewModel(key = "review-new-article-$reviewId") {
        NewArticleViewModel(db.articleFormDao(), db.categoryDao(), db.attributeDao(), images)
    }
    val newArticleState by newArticleViewModel.state.collectAsStateWithLifecycle()
    val createdArticleId by newArticleViewModel.created.collectAsStateWithLifecycle()

    // Article opened to check or fix its info (and its units), through the existing detail screen
    var editArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    var countLineId by rememberSaveable { mutableStateOf<String?>(null) }
    var flagLineId by rememberSaveable { mutableStateOf<String?>(null) }
    var elsewhereLineId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFound by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val timeZone = remember { TimeZone.currentSystemDefault() }

    LaunchedEffect(createdArticleId) {
        createdArticleId?.let {
            // The new article opens its detail, where its stock or units are added
            showFound = false
            editArticleId = it
            newArticleViewModel.consumeCreated()
        }
    }
    LaunchedEffect(openFound) {
        if (openFound) {
            showFound = true
            viewModel.consumeOpenFound()
        }
    }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbar.showSnackbar(
            getString(
                when (current) {
                    ReviewMessage.CLOSED -> Res.string.review_msg_closed
                    ReviewMessage.FAILED -> Res.string.review_msg_failed
                    ReviewMessage.FINISHED -> Res.string.review_msg_finished
                    ReviewMessage.TAG_FAILED -> Res.string.review_tag_failed
                    ReviewMessage.TAG_UNKNOWN -> Res.string.review_tag_unknown
                    ReviewMessage.TAG_NOT_IN_REVIEW -> Res.string.review_tag_not_in_review
                },
            ),
        )
    }
    LaunchedEffect(jumpTo, state.groups) {
        val lineId = jumpTo ?: return@LaunchedEffect
        val index = lazyIndexOf(state, lineId) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        viewModel.consumeJump()
    }

    val articleId = editArticleId
    if (articleId != null) {
        ArticleDetailScreen(articleId = articleId, onBack = {
            editArticleId = null
            viewModel.sync()
        })
        return
    }

    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
    // Listens only while no other sheet or screen is on top
    NfcTagListener(
        TagPriority.Lookup,
        enabled = state.isOpen && preview == null && newArticleState == null,
        onEvent = viewModel::onTag,
    )

    Box(modifier.fillMaxSize()) {
        SettingsSubpage(
            title = state.rootName,
            onBack = onBack,
            actions = {
                if (state.isOpen) {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(Res.drawable.ic_more_vert), contentDescription = stringResource(Res.string.review_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.review_cancel_review)) },
                            onClick = {
                                menu = false
                                confirmCancel = true
                            },
                        )
                    }
                }
            },
        ) {
            val review = state.review
            when {
                state.loading -> Unit
                review == null -> EmptyListHint(stringResource(Res.string.review_gone))
                else -> {
                    ReviewHeader(state, review.finishedAt?.let { formatDateTime(it, timeZone) }, viewModel::setFilter)
                    val emptyText = when {
                        state.progress.total == 0 -> stringResource(Res.string.review_empty)
                        state.groups.isEmpty() -> stringResource(Res.string.review_filter_empty)
                        else -> null
                    }
                    if (emptyText != null) {
                        Box(Modifier.weight(1f)) { EmptyListHint(emptyText) }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            state.groups.forEach { group ->
                                item(key = "group-${group.locationId}") {
                                    Text(
                                        group.path,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                                items(group.lines, key = { it.line.id }) { line ->
                                    LineCard(
                                        line = line,
                                        open = state.isOpen,
                                        highlighted = line.line.id == jumpTo,
                                        onCount = { countLineId = line.line.id },
                                        onStep = { delta ->
                                            val base = line.line.countedMilli ?: line.line.expectedMilli
                                            viewModel.setCount(line.line.id, (base + delta).coerceAtLeast(0))
                                        },
                                        onCorrect = { viewModel.markCorrect(line.line.id) },
                                        onResult = { result ->
                                            if (result == ReviewResult.ELSEWHERE) {
                                                elsewhereLineId = line.line.id
                                            } else {
                                                viewModel.setUnitResult(line.line.id, result)
                                            }
                                        },
                                        onReset = { viewModel.reset(line.line.id) },
                                        onFlag = { flagLineId = line.line.id },
                                        onEditInfo = { editArticleId = line.line.articleId },
                                    )
                                }
                            }
                        }
                    }
                    if (state.isOpen) {
                        HorizontalDivider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            OutlinedButton(onClick = { showFound = true }, modifier = Modifier.weight(1f)) {
                                Icon(painterResource(Res.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.review_add_found), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(onClick = viewModel::requestFinish, modifier = Modifier.weight(1f)) {
                                Text(stringResource(Res.string.review_finish))
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) { Snackbar(it) }
    }

    countLineId?.let { id ->
        val line = state.lines[id]
        if (line == null || !state.isOpen) {
            countLineId = null
        } else {
            CountDialog(line, onSave = {
                viewModel.setCount(id, it)
                countLineId = null
            }, onDismiss = { countLineId = null })
        }
    }
    flagLineId?.let { id ->
        val line = state.lines[id]
        if (line == null || !state.isOpen) {
            flagLineId = null
        } else {
            FlagDialog(line, onSave = { flagged, note ->
                viewModel.setFlag(id, flagged, note)
                flagLineId = null
            }, onDismiss = { flagLineId = null })
        }
    }
    elsewhereLineId?.let { id ->
        val line = state.lines[id]
        if (line == null || !state.isOpen) {
            elsewhereLineId = null
        } else {
            LocationPickerDialog(
                title = stringResource(Res.string.review_elsewhere_title),
                options = state.allLocations.filter { it.id != line.line.locationId },
                selectedId = line.line.foundLocationId,
                onSelect = {
                    viewModel.setUnitResult(id, ReviewResult.ELSEWHERE, it)
                    elsewhereLineId = null
                },
                onDismiss = { elsewhereLineId = null },
            )
        }
    }
    if (showFound && state.isOpen) {
        FoundItemDialog(
            state = state,
            search = search,
            onQueryChange = viewModel::onSearchChange,
            onAdd = { candidate, locationId ->
                viewModel.addFound(candidate, locationId)
                showFound = false
            },
            onNewArticle = { newArticleViewModel.start() },
            onDismiss = {
                viewModel.clearSearch()
                showFound = false
            },
        )
    }
    preview?.let { current ->
        val review = state.review
        if (review != null) {
            val comment = stringResource(Res.string.review_comment, state.rootName, formatDateTime(review.startedAt, timeZone))
            FinishDialog(
                state = state,
                preview = current,
                onResolution = viewModel::setResolution,
                onConfirm = { viewModel.confirmFinish(comment) {} },
                onDismiss = viewModel::dismissFinish,
            )
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(Res.string.review_cancel_title)) },
            text = { Text(stringResource(Res.string.review_cancel_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancelReview(onBack)
                }) { Text(stringResource(Res.string.review_cancel_review)) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(Res.string.review_keep)) } },
        )
    }
    newArticleState?.let { form ->
        ArticleFormDialog(
            state = form,
            onFormChange = newArticleViewModel::update,
            onImageImported = newArticleViewModel::onImageImported,
            onDismiss = newArticleViewModel::cancel,
            onSave = newArticleViewModel::save,
        )
    }
}

/** Index of [lineId] in the LazyColumn (group headers count as items). */
private fun lazyIndexOf(state: ReviewUiState, lineId: String): Int? {
    var index = 0
    for (group in state.groups) {
        index++
        val inGroup = group.lines.indexOfFirst { it.line.id == lineId }
        if (inGroup >= 0) return index + inGroup
        index += group.lines.size
    }
    return null
}

@Composable
private fun ReviewHeader(state: ReviewUiState, finishedAt: String?, onFilter: (LineFilter) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (state.rootPath != state.rootName) {
            Text(state.rootPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (finishedAt != null) {
            Text(
                stringResource(Res.string.review_read_only, finishedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val progress = state.progress
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.review_progress, progress.checked, progress.total), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (progress.discrepancies > 0) {
                Text(
                    stringResource(Res.string.review_progress_discrepancies, progress.discrepancies),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.checked.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        if (state.isOpen) {
            Text(stringResource(Res.string.review_tag_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        ) {
            LineFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilter(filter) },
                    label = {
                        Text(
                            stringResource(
                                when (filter) {
                                    LineFilter.ALL -> Res.string.review_filter_all
                                    LineFilter.UNCHECKED -> Res.string.review_filter_unchecked
                                    LineFilter.DISCREPANCIES -> Res.string.review_filter_discrepancies
                                    LineFilter.FLAGGED -> Res.string.review_filter_flagged
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LineCard(
    line: ReviewLineUi,
    open: Boolean,
    highlighted: Boolean,
    onCount: () -> Unit,
    onStep: (Long) -> Unit,
    onCorrect: () -> Unit,
    onResult: (ReviewResult) -> Unit,
    onReset: () -> Unit,
    onFlag: () -> Unit,
    onEditInfo: () -> Unit,
) {
    val colors = when {
        highlighted -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        line.status == LineStatus.DISCREPANCY -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        else -> CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(line.status)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (line.articleDeleted) stringResource(Res.string.review_article_deleted) else line.articleName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (line.line.isSerial) {
                        val status = line.itemStatus?.takeIf { it != ItemStatus.AVAILABLE }?.let { " · " + stringResource(it.label) } ?: ""
                        Text(
                            (line.serialNumber ?: stringResource(Res.string.review_no_serial)) + status,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (line.attributes.isNotEmpty()) {
                        Text(line.attributes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (open) LineMenu(line, onReset, onFlag, onEditInfo)
            }
            if (line.line.isAddedUnit) {
                Text(stringResource(Res.string.review_added_unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (line.line.result == ReviewResult.ELSEWHERE && line.foundPath != null) {
                Text(stringResource(Res.string.review_found_at, line.foundPath), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (line.line.infoFlagged) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(painterResource(Res.drawable.ic_flag), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        line.line.note?.let { stringResource(Res.string.review_flag_set, it) } ?: stringResource(Res.string.review_flag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (line.line.isSerial) UnitActions(line, open, onResult) else QuantityActions(line, open, onCount, onStep, onCorrect)
        }
    }
}

@Composable
private fun StatusIcon(status: LineStatus) {
    when (status) {
        LineStatus.OK -> Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        LineStatus.DISCREPANCY -> Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
        LineStatus.UNCHECKED -> Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = Color.Transparent)
    }
}

@Composable
private fun LineMenu(line: ReviewLineUi, onReset: () -> Unit, onFlag: () -> Unit, onEditInfo: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), contentDescription = stringResource(Res.string.review_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!line.articleDeleted) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.review_edit_info)) }, onClick = {
                    expanded = false
                    onEditInfo()
                })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.review_flag)) }, onClick = {
                expanded = false
                onFlag()
            })
            if (line.line.result != ReviewResult.UNCHECKED || line.line.isAddedUnit) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.review_reset)) }, onClick = {
                    expanded = false
                    onReset()
                })
            }
        }
    }
}

@Composable
private fun QuantityActions(line: ReviewLineUi, open: Boolean, onCount: () -> Unit, onStep: (Long) -> Unit, onCorrect: () -> Unit) {
    val counted = line.line.countedMilli
    Text(
        stringResource(Res.string.review_expected, formatQuantity(line.line.expectedMilli), line.unit) +
            (counted?.let { " · " + stringResource(Res.string.review_counted, formatQuantity(it), line.unit) } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (!open) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(-1000) }) {
            Icon(painterResource(Res.drawable.ic_remove), contentDescription = stringResource(Res.string.review_decrease))
        }
        TextButton(onClick = onCount) {
            Text(counted?.let { formatQuantity(it) } ?: stringResource(Res.string.review_count))
        }
        IconButton(onClick = { onStep(1000) }) {
            Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.review_increase))
        }
        Spacer(Modifier.weight(1f))
        FilledTonalButton(onClick = onCorrect, modifier = Modifier.padding(end = 8.dp)) {
            Text(stringResource(Res.string.review_correct))
        }
    }
}

@Composable
private fun UnitActions(line: ReviewLineUi, open: Boolean, onResult: (ReviewResult) -> Unit) {
    val choices = listOf(
        ReviewResult.PRESENT to Res.string.review_present,
        ReviewResult.MISSING to Res.string.review_missing,
        ReviewResult.ELSEWHERE to Res.string.review_elsewhere,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp, end = 12.dp).horizontalScroll(rememberScrollState()),
    ) {
        choices.forEach { (result, label) ->
            // An added unit is present (at its line location) or elsewhere, never missing
            if (result == ReviewResult.MISSING && line.line.isAddedUnit) return@forEach
            val selected = line.line.result == result
            FilterChip(
                selected = selected,
                enabled = open,
                onClick = { if (!selected || result == ReviewResult.ELSEWHERE) onResult(result) },
                label = { Text(stringResource(label)) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}
