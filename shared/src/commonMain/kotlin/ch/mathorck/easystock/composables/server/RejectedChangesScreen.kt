package ch.mathorck.easystock.composables.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.RejectedChange
import ch.mathorck.easystock.data.model.RejectedChangeRow
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.formatDateTime
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.rejected_at
import easystock.shared.generated.resources.rejected_deleted_row
import easystock.shared.generated.resources.rejected_dismiss
import easystock.shared.generated.resources.rejected_empty
import easystock.shared.generated.resources.rejected_hint
import easystock.shared.generated.resources.rejected_reason_conflict
import easystock.shared.generated.resources.rejected_reason_invalid
import easystock.shared.generated.resources.rejected_reason_missing_reference
import easystock.shared.generated.resources.rejected_reason_not_allowed
import easystock.shared.generated.resources.rejected_reason_review_open
import easystock.shared.generated.resources.rejected_reason_unknown
import easystock.shared.generated.resources.rejected_table_article
import easystock.shared.generated.resources.rejected_table_attribute_definition
import easystock.shared.generated.resources.rejected_table_category
import easystock.shared.generated.resources.rejected_table_category_attribute
import easystock.shared.generated.resources.rejected_table_item
import easystock.shared.generated.resources.rejected_table_location
import easystock.shared.generated.resources.rejected_table_stock_movement
import easystock.shared.generated.resources.rejected_table_stock_review
import easystock.shared.generated.resources.rejected_table_stock_review_line
import easystock.shared.generated.resources.rejected_title
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Settings › Server › Changes not accepted: the local changes the server refused, newest first.
 * Opening the screen marks them seen (the Settings badge goes away); each one is dismissed on its own.
 */
@Composable
fun RejectedChangesScreen(onBack: () -> Unit) {
    val db = rememberAppDatabase()
    val dao = remember(db) { db.syncDao() }
    val rowsFlow = remember(dao) { dao.observeRejectedChangeRows() }
    val rows by rowsFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    // Seen when shown, and again for those arriving while the screen is open
    LaunchedEffect(rows.size) { dao.markRejectedSeen(Clock.System.now().toEpochMilliseconds()) }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
    RejectedChangesContent(rows, onBack, onDismiss = { id -> scope.launch { dao.dismissRejectedChange(id) } })
}

@Composable
internal fun RejectedChangesContent(rows: List<RejectedChangeRow>, onBack: () -> Unit, onDismiss: (String) -> Unit) {
    SettingsSubpage(title = stringResource(Res.string.rejected_title), onBack = onBack) {
        if (rows.isEmpty()) {
            EmptyListHint(stringResource(Res.string.rejected_empty))
            return@SettingsSubpage
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(Res.string.rejected_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(rows, key = { it.change.id }) { row ->
                RejectedRow(row, onDismiss = { onDismiss(row.change.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RejectedRow(row: RejectedChangeRow, onDismiss: () -> Unit) {
    val change = row.change
    val table = tableLabel(change.tableName)?.let { stringResource(it) } ?: change.tableName
    ListItem(
        overlineContent = { Text(table) },
        headlineContent = { Text(row.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.rejected_deleted_row)) },
        supportingContent = {
            Column {
                Text(reasonText(change.reason), color = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(Res.string.rejected_at, formatDateTime(change.rejectedAt, TimeZone.currentSystemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.rejected_dismiss)) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

private fun tableLabel(table: String): StringResource? = when (table) {
    "category" -> Res.string.rejected_table_category
    "attribute_definition" -> Res.string.rejected_table_attribute_definition
    "category_attribute" -> Res.string.rejected_table_category_attribute
    "location" -> Res.string.rejected_table_location
    "article" -> Res.string.rejected_table_article
    "item" -> Res.string.rejected_table_item
    "stock_movement" -> Res.string.rejected_table_stock_movement
    "stock_review" -> Res.string.rejected_table_stock_review
    "stock_review_line" -> Res.string.rejected_table_stock_review_line
    else -> null
}

@Composable
private fun reasonText(reason: String): String = when (reason) {
    "not-allowed" -> stringResource(Res.string.rejected_reason_not_allowed)
    "invalid" -> stringResource(Res.string.rejected_reason_invalid)
    "missing-reference" -> stringResource(Res.string.rejected_reason_missing_reference)
    "review-open" -> stringResource(Res.string.rejected_reason_review_open)
    "conflict" -> stringResource(Res.string.rejected_reason_conflict)
    else -> stringResource(Res.string.rejected_reason_unknown, reason)
}

@Preview(showBackground = true)
@Composable
private fun RejectedChangesPreview() {
    EasyStockTheme {
        RejectedChangesContent(
            rows = listOf(
                RejectedChangeRow(RejectedChange("r1", "article", "a1", "not-allowed", 1762859000000), "Drill"),
                RejectedChangeRow(RejectedChange("r2", "stock_review", "s1", "review-open", 1762850000000), "Workshop"),
                RejectedChangeRow(RejectedChange("r3", "category", "c1", "weird-code", 1762840000000), null),
            ),
            onBack = {},
            onDismiss = {},
        )
    }
}
