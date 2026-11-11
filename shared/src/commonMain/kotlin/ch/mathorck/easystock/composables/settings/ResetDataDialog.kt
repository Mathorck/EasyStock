package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ch.mathorck.easystock.composables.rememberAutoFocusRequester
import ch.mathorck.easystock.reset.matchesConfirmation
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.app_info_reset_backup
import easystock.shared.generated.resources.app_info_reset_confirm
import easystock.shared.generated.resources.app_info_reset_failed
import easystock.shared.generated.resources.app_info_reset_message
import easystock.shared.generated.resources.app_info_reset_prompt
import easystock.shared.generated.resources.app_info_reset_running
import easystock.shared.generated.resources.app_info_reset_title
import easystock.shared.generated.resources.app_info_reset_word
import easystock.shared.generated.resources.ic_backup
import easystock.shared.generated.resources.ic_warning
import easystock.shared.generated.resources.settings_cancel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Confirms the reset of all data: explains what is erased, offers a backup first, and needs the
 * localized confirmation word typed (see [matchesConfirmation]). Not dismissable while [running].
 */
@Composable
internal fun ResetDataDialog(
    running: Boolean,
    failed: Boolean,
    onConfirm: () -> Unit,
    onOpenBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val word = stringResource(Res.string.app_info_reset_word)
    var typed by rememberSaveable { mutableStateOf("") }
    val confirmed = matchesConfirmation(typed, word)
    val focusRequester = rememberAutoFocusRequester()
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !running, dismissOnClickOutside = !running),
        icon = { Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.app_info_reset_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.app_info_reset_message), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpenBackup, enabled = !running, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(painterResource(Res.drawable.ic_backup), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(Res.string.app_info_reset_backup))
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(Res.string.app_info_reset_prompt, word)) },
                    singleLine = true,
                    enabled = !running,
                    isError = failed,
                    supportingText = if (failed) {
                        { Text(stringResource(Res.string.app_info_reset_failed)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmed && !running,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(if (running) Res.string.app_info_reset_running else Res.string.app_info_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text(stringResource(Res.string.settings_cancel)) }
        },
    )
}
