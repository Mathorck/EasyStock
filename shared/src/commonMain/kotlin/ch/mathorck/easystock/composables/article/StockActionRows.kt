package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.data.db.UnitAction
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.LocationStockRow
import ch.mathorck.easystock.viewmodels.UnitRow
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_more_vert
import easystock.shared.generated.resources.ic_remove
import easystock.shared.generated.resources.ic_swap_horiz
import easystock.shared.generated.resources.stock_action_add
import easystock.shared.generated.resources.stock_action_end_repair
import easystock.shared.generated.resources.stock_action_found
import easystock.shared.generated.resources.stock_action_lend
import easystock.shared.generated.resources.stock_action_lose
import easystock.shared.generated.resources.stock_action_more
import easystock.shared.generated.resources.stock_action_move
import easystock.shared.generated.resources.stock_action_no_serial
import easystock.shared.generated.resources.stock_action_not_in_stock
import easystock.shared.generated.resources.stock_action_remove
import easystock.shared.generated.resources.stock_action_repair
import easystock.shared.generated.resources.stock_action_return
import easystock.shared.generated.resources.stock_action_status_available
import easystock.shared.generated.resources.stock_action_status_lent
import easystock.shared.generated.resources.stock_action_status_lost
import easystock.shared.generated.resources.stock_action_status_repair
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Stock at one location with move, remove and add buttons. */
@Composable
internal fun LocationActionRow(
    row: LocationStockRow,
    unit: String,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.path, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${formatQuantity(row.stockMilli)} $unit",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onMove) {
            Icon(painterResource(Res.drawable.ic_swap_horiz), contentDescription = stringResource(Res.string.stock_action_move))
        }
        RoundAction(Res.drawable.ic_remove, Res.string.stock_action_remove, onRemove)
        RoundAction(Res.drawable.ic_add, Res.string.stock_action_add, onAdd)
    }
}

@Composable
private fun RoundAction(icon: DrawableResource, label: StringResource, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp).size(44.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

/** One serial unit: serial number, location, status chip and an actions menu. */
@Composable
internal fun UnitActionRow(unit: UnitRow, onMove: () -> Unit, onAction: (UnitAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val outOfStock = unit.status == ItemStatus.LOST
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                unit.serial ?: stringResource(Res.string.stock_action_no_serial),
                style = MaterialTheme.typography.bodyLarge,
                color = if (outOfStock) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                unit.path ?: stringResource(Res.string.stock_action_not_in_stock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(unit.status)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(Res.drawable.ic_more_vert), contentDescription = stringResource(Res.string.stock_action_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (unit.canMove) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.stock_action_move)) },
                        onClick = {
                            menu = false
                            onMove()
                        },
                    )
                }
                unit.actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.label)) },
                        onClick = {
                            menu = false
                            onAction(action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ItemStatus) {
    val (container, content) = when (status) {
        ItemStatus.AVAILABLE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ItemStatus.LENT -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ItemStatus.REPAIR -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ItemStatus.LOST -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            stringResource(status.label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Full-width outlined button with a leading icon, e.g. "Add to a location". */
@Composable
internal fun StockActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Icon(painterResource(Res.drawable.ic_add), contentDescription = null)
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

private val UnitAction.label: StringResource
    get() = when (this) {
        UnitAction.LEND -> Res.string.stock_action_lend
        UnitAction.RETURN -> Res.string.stock_action_return
        UnitAction.REPAIR -> Res.string.stock_action_repair
        UnitAction.END_REPAIR -> Res.string.stock_action_end_repair
        UnitAction.LOSE -> Res.string.stock_action_lose
        UnitAction.FOUND -> Res.string.stock_action_found
    }

private val ItemStatus.label: StringResource
    get() = when (this) {
        ItemStatus.AVAILABLE -> Res.string.stock_action_status_available
        ItemStatus.LENT -> Res.string.stock_action_status_lent
        ItemStatus.REPAIR -> Res.string.stock_action_status_repair
        ItemStatus.LOST -> Res.string.stock_action_status_lost
    }
