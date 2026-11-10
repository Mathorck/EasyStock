package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ch.mathorck.easystock.composables.attributes.AttributeFields
import ch.mathorck.easystock.data.db.CREATABLE_STATUSES
import ch.mathorck.easystock.data.db.editableStatuses
import ch.mathorck.easystock.viewmodels.StockActionForm
import ch.mathorck.easystock.viewmodels.StockActionKind
import ch.mathorck.easystock.viewmodels.StockActionUiState
import ch.mathorck.easystock.viewmodels.itemAttributeOverrides
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.item_archived
import easystock.shared.generated.resources.item_attributes_hint
import easystock.shared.generated.resources.item_attributes_section
import easystock.shared.generated.resources.item_status
import easystock.shared.generated.resources.item_status_locked_hint
import easystock.shared.generated.resources.stock_action_serial
import org.jetbrains.compose.resources.stringResource

/**
 * The properties of a serial unit: serial number, status, attribute values that override the article ones
 * and (edit only) the archived flag. Shared by the "add unit" and the "edit unit" sheets.
 * The location is not here: it comes from the movements.
 */
@Composable
internal fun ItemPropertiesFields(state: StockActionUiState, onFormChange: ((StockActionForm) -> StockActionForm) -> Unit) {
    val form = state.form
    ActionField(
        value = form.serial,
        onValueChange = { v -> onFormChange { it.copy(serial = v) } },
        label = stringResource(Res.string.stock_action_serial),
        error = state.errors.serial,
    )

    val statuses = if (form.kind == StockActionKind.ADD_UNIT) CREATABLE_STATUSES else editableStatuses(state.currentStatus ?: form.itemStatus)
    Text(stringResource(Res.string.item_status), style = MaterialTheme.typography.labelLarge)
    if (statuses.size > 1) {
        Choice(
            options = statuses,
            selected = form.itemStatus,
            label = { stringResource(it.label) },
            onSelect = { status -> onFormChange { it.copy(itemStatus = status) } },
        )
    } else {
        Info(stringResource(form.itemStatus.label))
        Info(stringResource(Res.string.item_status_locked_hint))
    }

    if (state.itemAttributes.isNotEmpty()) {
        Text(stringResource(Res.string.item_attributes_section), style = MaterialTheme.typography.labelLarge)
        Info(stringResource(Res.string.item_attributes_hint))
        AttributeFields(
            attributes = itemAttributeOverrides(state.itemAttributes),
            values = form.itemAttributes,
            onValuesChange = { values -> onFormChange { it.copy(itemAttributes = values) } },
            errors = state.errors.attributes,
            inherited = state.inheritedAttributes,
            overrides = true,
        )
    }

    if (form.kind == StockActionKind.EDIT_UNIT) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.item_archived), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = form.archived, onCheckedChange = { v -> onFormChange { it.copy(archived = v) } })
        }
    }
}
