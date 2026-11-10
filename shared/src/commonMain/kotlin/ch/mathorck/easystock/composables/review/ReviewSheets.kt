package ch.mathorck.easystock.composables.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.mathorck.easystock.composables.article.label
import ch.mathorck.easystock.data.db.FoundCandidate
import ch.mathorck.easystock.review.MissingResolution
import ch.mathorck.easystock.review.ReviewChange
import ch.mathorck.easystock.review.SkipReason
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.FoundSearchState
import ch.mathorck.easystock.viewmodels.ReviewFinishPreview
import ch.mathorck.easystock.viewmodels.ReviewLineUi
import ch.mathorck.easystock.viewmodels.ReviewUiState
import ch.mathorck.easystock.viewmodels.formatSignedQuantity
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.ic_search
import easystock.shared.generated.resources.review_add_found
import easystock.shared.generated.resources.review_cancel
import easystock.shared.generated.resources.review_change_back
import easystock.shared.generated.resources.review_change_correction
import easystock.shared.generated.resources.review_change_lose
import easystock.shared.generated.resources.review_change_move
import easystock.shared.generated.resources.review_clamped
import easystock.shared.generated.resources.review_drifted
import easystock.shared.generated.resources.review_finish_changes
import easystock.shared.generated.resources.review_finish_confirm
import easystock.shared.generated.resources.review_finish_note
import easystock.shared.generated.resources.review_finish_nothing
import easystock.shared.generated.resources.review_finish_title
import easystock.shared.generated.resources.review_finish_unchecked
import easystock.shared.generated.resources.review_found_location
import easystock.shared.generated.resources.review_found_new
import easystock.shared.generated.resources.review_found_new_hint
import easystock.shared.generated.resources.review_found_none
import easystock.shared.generated.resources.review_found_search
import easystock.shared.generated.resources.review_missing_keep
import easystock.shared.generated.resources.review_missing_lose
import easystock.shared.generated.resources.review_missing_title
import easystock.shared.generated.resources.review_no_serial
import easystock.shared.generated.resources.review_skipped_changed
import easystock.shared.generated.resources.review_skipped_gone
import easystock.shared.generated.resources.review_skipped_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Full-screen dialog with a title, a scrolling body and a bottom row of buttons. */
@Composable
private fun FullDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Column(Modifier.weight(1f)) { content() }
                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { buttons() }
            }
        }
    }
}

/** "Add found item": search an article or unit, choose where it was found, or create a new article. */
@Composable
internal fun FoundItemDialog(
    state: ReviewUiState,
    search: FoundSearchState,
    onQueryChange: (String) -> Unit,
    onAdd: (FoundCandidate, String) -> Unit,
    onNewArticle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rootId = state.review?.locationId
    var locationId by rememberSaveable { mutableStateOf(rootId) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val location = state.scopeLocations.firstOrNull { it.id == locationId } ?: state.scopeLocations.firstOrNull()
    FullDialog(
        title = stringResource(Res.string.review_add_found),
        onDismiss = onDismiss,
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.review_cancel)) } },
    ) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                stringResource(Res.string.review_found_location) + ": " + (location?.path ?: ""),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedTextField(
            value = search.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(Res.string.review_found_search)) },
            leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(search.results, key = { (it.itemId ?: "") + "/" + it.articleId }) { candidate ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = location != null) { location?.let { onAdd(candidate, it.id) } }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(candidate.articleName, style = MaterialTheme.typography.bodyLarge)
                    if (candidate.itemId != null) {
                        val details = listOfNotNull(
                            candidate.serialNumber ?: stringResource(Res.string.review_no_serial),
                            candidate.itemStatus?.let { stringResource(it.label) },
                            candidate.locationId?.let { state.paths[it] },
                        )
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(candidate.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (search.query.isNotBlank() && !search.searching && search.results.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.review_found_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(Res.string.review_found_new_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNewArticle) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.review_found_new))
            }
        }
    }
    if (picking) {
        LocationPickerDialog(
            title = stringResource(Res.string.review_found_location),
            options = state.scopeLocations,
            selectedId = location?.id,
            onSelect = {
                locationId = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Summary of what finishing changes. Nothing is written until [onConfirm]. */
@Composable
internal fun FinishDialog(
    state: ReviewUiState,
    preview: ReviewFinishPreview,
    onResolution: (String, MissingResolution) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val plan = preview.plan
    FullDialog(
        title = stringResource(Res.string.review_finish_title),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.review_cancel)) }
            Button(onClick = onConfirm, enabled = !preview.finishing) { Text(stringResource(Res.string.review_finish_confirm)) }
        },
    ) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(stringResource(Res.string.review_finish_note), style = MaterialTheme.typography.bodyMedium)
            }
            if (plan.unchecked > 0) {
                item {
                    Text(
                        stringResource(Res.string.review_finish_unchecked, plan.unchecked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val missing = preview.missingLineIds.mapNotNull { state.lines[it] }
            if (missing.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.review_missing_title)) }
                items(missing, key = { "missing-" + it.line.id }) { line ->
                    val resolution = preview.resolutions[line.line.id] ?: MissingResolution.LOSE
                    Column {
                        Text(lineLabel(line), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ResolutionChip(stringResource(Res.string.review_missing_lose), resolution == MissingResolution.LOSE) {
                                onResolution(line.line.id, MissingResolution.LOSE)
                            }
                            ResolutionChip(stringResource(Res.string.review_missing_keep), resolution == MissingResolution.KEEP) {
                                onResolution(line.line.id, MissingResolution.KEEP)
                            }
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(Res.string.review_finish_changes)) }
            if (plan.changes.isEmpty()) {
                item { Text(stringResource(Res.string.review_finish_nothing), style = MaterialTheme.typography.bodyMedium) }
            }
            items(plan.changes, key = { "change-" + it.lineId }) { change ->
                val line = state.lines[change.lineId]
                val name = line?.articleName ?: ""
                val serial = line?.serialNumber ?: stringResource(Res.string.review_no_serial)
                Column {
                    Text(
                        when (change) {
                            is ReviewChange.Correction -> stringResource(
                                Res.string.review_change_correction,
                                name,
                                state.paths[change.locationId] ?: "",
                                formatQuantity(change.currentMilli),
                                formatQuantity(change.currentMilli + change.deltaMilli),
                                line?.unit ?: "",
                            ) + " (" + formatSignedQuantity(change.deltaMilli) + ")"
                            is ReviewChange.MoveUnit -> stringResource(
                                Res.string.review_change_move,
                                name,
                                serial,
                                state.paths[change.fromLocationId] ?: "",
                                state.paths[change.toLocationId] ?: "",
                            )
                            is ReviewChange.BringBack -> stringResource(Res.string.review_change_back, name, serial, state.paths[change.locationId] ?: "")
                            is ReviewChange.LoseUnit -> stringResource(Res.string.review_change_lose, name, serial)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (change.lineId in plan.drifted) Note(stringResource(Res.string.review_drifted))
                    if (change.lineId in plan.clamped) Note(stringResource(Res.string.review_clamped))
                }
            }
            if (plan.skipped.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.review_skipped_title)) }
                items(plan.skipped, key = { "skipped-" + it.lineId }) { skipped ->
                    val label = state.lines[skipped.lineId]?.let { lineLabel(it) } ?: ""
                    Text(
                        stringResource(
                            if (skipped.reason == SkipReason.GONE) Res.string.review_skipped_gone else Res.string.review_skipped_changed,
                            label,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun lineLabel(line: ReviewLineUi): String =
    if (line.serialNumber != null || line.line.itemId != null) {
        line.articleName + " (" + (line.serialNumber ?: stringResource(Res.string.review_no_serial)) + ")"
    } else {
        line.articleName
    }

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ResolutionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}
