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
import ch.mathorck.easystock.composables.article.historyLabel
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.RejectedChangeRow
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.sync.KeepMineBlock
import ch.mathorck.easystock.sync.MovementKeptDetails
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.formatDateTime
import ch.mathorck.easystock.viewmodels.formatSignedQuantity
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.movement_kept_blocked_article_gone
import easystock.shared.generated.resources.movement_kept_blocked_invalid
import easystock.shared.generated.resources.movement_kept_blocked_location_gone
import easystock.shared.generated.resources.movement_kept_blocked_not_enough_stock
import easystock.shared.generated.resources.movement_kept_blocked_serial
import easystock.shared.generated.resources.movement_kept_blocked_transfer
import easystock.shared.generated.resources.movement_kept_keep_mine
import easystock.shared.generated.resources.movement_kept_keep_mine_hint
import easystock.shared.generated.resources.movement_kept_phone
import easystock.shared.generated.resources.movement_kept_reason
import easystock.shared.generated.resources.movement_kept_server
import easystock.shared.generated.resources.movement_kept_use_server
import easystock.shared.generated.resources.rejected_at
import easystock.shared.generated.resources.rejected_deleted_row
import easystock.shared.generated.resources.rejected_table_stock_movement
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A `movement-kept` change (sync/MovementKept): both versions, and the choice. It stays in the
 * list until the user picks one. "Keep mine" is only offered when the role and the stock allow it.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun MovementKeptRow(row: RejectedChangeRow) {
    val dao = rememberAppDatabase().syncDao()
    val canStockAction = LocalPermissions.current.can(Action.STOCK_ACTION)
    val scope = rememberCoroutineScope()
    // Read again after a refused "Keep mine" (the stock changed meanwhile)
    var reload by remember { mutableIntStateOf(0) }
    val details by produceState<MovementKeptDetails?>(null, row.change.id, canStockAction, reload) {
        value = dao.movementKeptDetails(row.change.id, canStockAction)
    }
    MovementKeptContent(
        row = row,
        details = details,
        onUseServer = { scope.launch { dao.useServerMovement(row.change.id) } },
        onKeepMine = {
            scope.launch {
                val now = Clock.System.now().toEpochMilliseconds()
                if (dao.keepMyMovement(row.change.id, Uuid.random().toString(), canStockAction, now) != null) reload++
            }
        },
    )
}

@Composable
private fun MovementKeptContent(row: RejectedChangeRow, details: MovementKeptDetails?, onUseServer: () -> Unit, onKeepMine: () -> Unit) {
    ListItem(
        overlineContent = { Text(stringResource(Res.string.rejected_table_stock_movement)) },
        headlineContent = { Text(row.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.rejected_deleted_row)) },
        supportingContent = {
            Column {
                Text(stringResource(Res.string.movement_kept_reason), color = MaterialTheme.colorScheme.error)
                details?.server?.let { Text(stringResource(Res.string.movement_kept_server, it.quantityText(), stringResource(it.reason.historyLabel))) }
                details?.phone?.let { Text(stringResource(Res.string.movement_kept_phone, it.quantityText(), stringResource(it.reason.historyLabel))) }
                Text(
                    stringResource(Res.string.rejected_at, formatDateTime(row.change.rejectedAt, TimeZone.currentSystemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                )
                val block = details?.keepMineBlock
                when {
                    details == null -> Unit
                    block == null -> Text(stringResource(Res.string.movement_kept_keep_mine_hint), style = MaterialTheme.typography.bodySmall)
                    else -> blockText(block)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                }
                FlowRow {
                    TextButton(onClick = onUseServer) { Text(stringResource(Res.string.movement_kept_use_server)) }
                    if (details != null && block == null) {
                        TextButton(onClick = onKeepMine) { Text(stringResource(Res.string.movement_kept_keep_mine)) }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

private fun StockMovement.quantityText(): String = formatSignedQuantity(quantityMilli)

/** Null for the role: a Viewer is not told about an action they can't do anyway. */
private fun blockText(block: KeepMineBlock): StringResource? = when (block) {
    KeepMineBlock.ROLE -> null
    KeepMineBlock.SERIAL_UNIT -> Res.string.movement_kept_blocked_serial
    KeepMineBlock.TRANSFER -> Res.string.movement_kept_blocked_transfer
    KeepMineBlock.ARTICLE_GONE -> Res.string.movement_kept_blocked_article_gone
    KeepMineBlock.LOCATION_GONE -> Res.string.movement_kept_blocked_location_gone
    KeepMineBlock.NOT_ENOUGH_STOCK -> Res.string.movement_kept_blocked_not_enough_stock
    KeepMineBlock.INVALID -> Res.string.movement_kept_blocked_invalid
}

@Preview(showBackground = true)
@Composable
private fun MovementKeptPreview() {
    val server = StockMovement("m1", "a1", null, "l1", 2000, MovementReason.PURCHASE, null, 5, 5)
    EasyStockTheme {
        MovementKeptContent(
            row = RejectedChangeRow(
                ch.mathorck.easystock.data.model.RejectedChange("r1", "stock_movement", "m1", "movement-kept:{}", 1762859000000),
                "Drill",
            ),
            details = MovementKeptDetails(phone = server.copy(quantityMilli = 3000), server = server, keepMineBlock = null),
            onUseServer = {},
            onKeepMine = {},
        )
    }
}
