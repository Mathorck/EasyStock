package ch.mathorck.easystock.composables.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import ch.mathorck.easystock.auth.Action
import ch.mathorck.easystock.auth.LocalPermissions
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.RejectedChange
import ch.mathorck.easystock.data.model.RejectedChangeRow
import ch.mathorck.easystock.sync.RowKeepMineBlock
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.formatDateTime
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.rejected_at
import easystock.shared.generated.resources.rejected_deleted_row
import easystock.shared.generated.resources.row_conflict_blocked_gone
import easystock.shared.generated.resources.row_conflict_blocked_invalid
import easystock.shared.generated.resources.row_conflict_blocked_taken
import easystock.shared.generated.resources.row_conflict_keep_mine
import easystock.shared.generated.resources.row_conflict_keep_mine_hint
import easystock.shared.generated.resources.row_conflict_keep_theirs
import easystock.shared.generated.resources.row_conflict_reason
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What "Keep mine" can do for a row conflict: not read yet, offered, or why not. */
private sealed interface KeepMine {
    data object Loading : KeepMine

    data object Offered : KeepMine

    data class Blocked(val block: RowKeepMineBlock) : KeepMine
}

/**
 * A stale edit the server rejected (`conflict:`, sync/RowConflict): the phone shows the server's
 * version; the user keeps it, or sends their own again. It stays in the list until the user picks.
 */
@Composable
internal fun RowConflictRow(row: RejectedChangeRow) {
    val dao = rememberAppDatabase().syncDao()
    val canPush = LocalPermissions.current.can(Action.PUSH_CHANGES)
    val scope = rememberCoroutineScope()
    // Read again after a refused "Keep mine" (a value got taken meanwhile)
    var reload by remember { mutableIntStateOf(0) }
    val keepMine by produceState<KeepMine>(KeepMine.Loading, row.change.id, canPush, reload) {
        value = dao.rowKeepMineBlock(row.change.id, canPush)?.let { KeepMine.Blocked(it) } ?: KeepMine.Offered
    }
    RowConflictContent(
        row = row,
        keepMine = keepMine,
        onKeepTheirs = { scope.launch { dao.keepTheirRow(row.change.id) } },
        onKeepMine = {
            scope.launch {
                val now = Clock.System.now().toEpochMilliseconds()
                val block = try {
                    dao.keepMyRow(row.change.id, canPush, now)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The database refused the phone's version (a parent gone): nothing was written
                    RowKeepMineBlock.INVALID
                }
                if (block != null) reload++
            }
        },
    )
}

@Composable
private fun RowConflictContent(row: RejectedChangeRow, keepMine: KeepMine, onKeepTheirs: () -> Unit, onKeepMine: () -> Unit) {
    val change = row.change
    ListItem(
        overlineContent = { Text(tableLabel(change.tableName)?.let { stringResource(it) } ?: change.tableName) },
        headlineContent = { Text(row.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.rejected_deleted_row)) },
        supportingContent = {
            Column {
                Text(stringResource(Res.string.row_conflict_reason), color = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(Res.string.rejected_at, formatDateTime(change.rejectedAt, TimeZone.currentSystemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                )
                when (keepMine) {
                    KeepMine.Loading -> Unit
                    KeepMine.Offered -> Text(stringResource(Res.string.row_conflict_keep_mine_hint), style = MaterialTheme.typography.bodySmall)
                    is KeepMine.Blocked -> blockText(keepMine.block)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                }
                FlowRow {
                    TextButton(onClick = onKeepTheirs) { Text(stringResource(Res.string.row_conflict_keep_theirs)) }
                    if (keepMine == KeepMine.Offered) {
                        TextButton(onClick = onKeepMine) { Text(stringResource(Res.string.row_conflict_keep_mine)) }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Null for the role: a Viewer is not told about an action they can't do anyway. */
private fun blockText(block: RowKeepMineBlock): StringResource? = when (block) {
    RowKeepMineBlock.ROLE -> null
    RowKeepMineBlock.ROW_GONE -> Res.string.row_conflict_blocked_gone
    RowKeepMineBlock.VALUE_TAKEN -> Res.string.row_conflict_blocked_taken
    RowKeepMineBlock.INVALID -> Res.string.row_conflict_blocked_invalid
}

@Preview(showBackground = true)
@Composable
private fun RowConflictPreview() {
    EasyStockTheme {
        RowConflictContent(
            row = RejectedChangeRow(RejectedChange("r1", "article", "a1", "conflict:{}", 1762859000000), "Drill"),
            keepMine = KeepMine.Offered,
            onKeepTheirs = {},
            onKeepMine = {},
        )
    }
}
