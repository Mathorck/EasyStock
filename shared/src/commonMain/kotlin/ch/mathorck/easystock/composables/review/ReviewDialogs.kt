package ch.mathorck.easystock.composables.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.util.parseQuantity
import ch.mathorck.easystock.viewmodels.LocationOption
import ch.mathorck.easystock.viewmodels.ReviewLineUi
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_remove
import easystock.shared.generated.resources.review_cancel
import easystock.shared.generated.resources.review_count_invalid
import easystock.shared.generated.resources.review_count_label
import easystock.shared.generated.resources.review_decrease
import easystock.shared.generated.resources.review_flag
import easystock.shared.generated.resources.review_flag_note
import easystock.shared.generated.resources.review_flag_remove
import easystock.shared.generated.resources.review_increase
import easystock.shared.generated.resources.review_save
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Count of a quantity line, typed or with the stepper. Starts from the last count, or the expected quantity. */
@Composable
internal fun CountDialog(line: ReviewLineUi, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(line.line.id) {
        mutableStateOf(formatQuantity(line.line.countedMilli ?: line.line.expectedMilli))
    }
    val parsed = parseQuantity(text)?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.articleName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { text = formatQuantity(((parsed ?: 0) - 1000).coerceAtLeast(0)) }) {
                    Icon(painterResource(Res.drawable.ic_remove), contentDescription = stringResource(Res.string.review_decrease))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.review_count_label)) },
                    suffix = { Text(line.unit) },
                    singleLine = true,
                    isError = parsed == null,
                    supportingText = if (parsed == null) {
                        { Text(stringResource(Res.string.review_count_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { text = formatQuantity((parsed ?: 0) + 1000) }) {
                    Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.review_increase))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text(stringResource(Res.string.review_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.review_cancel)) } },
    )
}

/** Flags a line as "info to fix", with an optional note. */
@Composable
internal fun FlagDialog(line: ReviewLineUi, onSave: (Boolean, String?) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable(line.line.id) { mutableStateOf(line.line.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.review_flag)) },
        text = {
            Column {
                Text(line.articleName + (line.serialNumber?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= MAX_NOTE_LENGTH) note = it },
                    label = { Text(stringResource(Res.string.review_flag_note)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(true, note) }) { Text(stringResource(Res.string.review_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (line.line.infoFlagged) {
                    TextButton(onClick = { onSave(false, null) }) { Text(stringResource(Res.string.review_flag_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.review_cancel)) }
            }
        },
    )
}

// Max length from docs/schema.dbml (stock_review_line.note)
internal const val MAX_NOTE_LENGTH = 500

/** Picks one location of [options], shown as an indented tree. [hint] is shown above the list. */
@Composable
internal fun LocationPickerDialog(
    title: String,
    options: List<LocationOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
    selectedId: String? = null,
) {
    val minDepth = remember(options) { options.minOfOrNull { it.depth } ?: 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(options, key = { it.id }) { option ->
                        Text(
                            option.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option.id == selectedId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option.id) }
                                .padding(start = (16 * (option.depth - minDepth)).dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.review_cancel)) } },
    )
}
