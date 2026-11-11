package ch.mathorck.easystock.composables.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.ReviewStatus
import ch.mathorck.easystock.viewmodels.ReviewListRow
import ch.mathorck.easystock.viewmodels.ReviewListViewModel
import ch.mathorck.easystock.viewmodels.ReviewMessage
import ch.mathorck.easystock.viewmodels.formatDateTime
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_fact_check
import easystock.shared.generated.resources.review_list_counts
import easystock.shared.generated.resources.review_list_empty
import easystock.shared.generated.resources.review_list_finished
import easystock.shared.generated.resources.review_list_flagged
import easystock.shared.generated.resources.review_list_started
import easystock.shared.generated.resources.review_list_title
import easystock.shared.generated.resources.review_location_deleted
import easystock.shared.generated.resources.review_msg_failed
import easystock.shared.generated.resources.review_no_locations
import easystock.shared.generated.resources.review_start
import easystock.shared.generated.resources.review_start_hint
import easystock.shared.generated.resources.review_start_title
import easystock.shared.generated.resources.review_status_finished
import easystock.shared.generated.resources.review_status_open
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Settings > Stock reviews: open reviews first, then the finished ones. Starts a review on a location. */
@Composable
fun ReviewsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val viewModel = viewModel { ReviewListViewModel(db.reviewDao()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var reviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val timeZone = remember { TimeZone.currentSystemDefault() }

    LaunchedEffect(opened) {
        opened?.let {
            reviewId = it
            viewModel.consumeOpened()
        }
    }
    LaunchedEffect(message) {
        if (message == ReviewMessage.FAILED) {
            snackbar.showSnackbar(getString(Res.string.review_msg_failed))
            viewModel.consumeMessage()
        }
    }

    val current = reviewId
    if (current != null) {
        ReviewScreen(reviewId = current, onBack = { reviewId = null }, modifier = modifier)
        return
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    Box(modifier.fillMaxSize()) {
        SettingsSubpage(title = stringResource(Res.string.review_list_title), onBack = onBack) {
            when {
                state.loading -> Unit
                state.rows.isEmpty() -> EmptyListHint(
                    stringResource(if (state.locations.isEmpty()) Res.string.review_no_locations else Res.string.review_list_empty),
                )
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
                    items(state.rows, key = { it.summary.id }) { row ->
                        ReviewRow(row, timeZone, onClick = { reviewId = row.summary.id })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
        if (state.locations.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(painterResource(Res.drawable.ic_fact_check), contentDescription = null) },
                text = { Text(stringResource(Res.string.review_start)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) { Snackbar(it) }
    }
    if (picking) {
        LocationPickerDialog(
            title = stringResource(Res.string.review_start_title),
            hint = stringResource(Res.string.review_start_hint),
            options = state.locations,
            onSelect = {
                picking = false
                viewModel.start(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ReviewRow(row: ReviewListRow, timeZone: TimeZone, onClick: () -> Unit) {
    val summary = row.summary
    val open = summary.status == ReviewStatus.OPEN
    val date = formatDateTime(summary.finishedAt ?: summary.startedAt, timeZone)
    ListItem(
        headlineContent = {
            Text(if (row.locationDeleted) stringResource(Res.string.review_location_deleted) else row.path)
        },
        overlineContent = {
            Text(
                stringResource(if (open) Res.string.review_status_open else Res.string.review_status_finished),
                color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            val counts = stringResource(Res.string.review_list_counts, summary.checked, summary.total, summary.discrepancies)
            val flagged = if (summary.flagged > 0) " · " + stringResource(Res.string.review_list_flagged, summary.flagged) else ""
            val dateText = stringResource(if (open) Res.string.review_list_started else Res.string.review_list_finished, date)
            Text("$dateText\n$counts$flagged")
        },
        leadingContent = { Icon(painterResource(Res.drawable.ic_fact_check), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
