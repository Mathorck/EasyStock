package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.backup.BACKUP_FILE_EXTENSION
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.LocalFile
import ch.mathorck.easystock.server.ServerImportMode
import ch.mathorck.easystock.server.rememberServerFiles
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.admin.ServerBackupMessage
import ch.mathorck.easystock.viewmodels.admin.ServerBackupState
import ch.mathorck.easystock.viewmodels.admin.ServerBackupViewModel
import ch.mathorck.easystock.viewmodels.admin.ServerImportForm
import ch.mathorck.easystock.viewmodels.admin.canImport
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_backup
import easystock.shared.generated.resources.admin_backup_download
import easystock.shared.generated.resources.admin_backup_download_summary
import easystock.shared.generated.resources.admin_backup_downloaded
import easystock.shared.generated.resources.admin_backup_import
import easystock.shared.generated.resources.admin_backup_import_send
import easystock.shared.generated.resources.admin_backup_import_summary
import easystock.shared.generated.resources.admin_backup_import_title
import easystock.shared.generated.resources.admin_backup_imported
import easystock.shared.generated.resources.admin_backup_merge
import easystock.shared.generated.resources.admin_backup_merge_summary
import easystock.shared.generated.resources.admin_backup_replace
import easystock.shared.generated.resources.admin_backup_replace_prompt
import easystock.shared.generated.resources.admin_backup_replace_summary
import easystock.shared.generated.resources.admin_backup_replace_word
import easystock.shared.generated.resources.admin_backup_save
import easystock.shared.generated.resources.admin_backup_saved
import easystock.shared.generated.resources.admin_backup_share
import easystock.shared.generated.resources.admin_cancel
import easystock.shared.generated.resources.admin_error_file
import easystock.shared.generated.resources.ic_download
import easystock.shared.generated.resources.ic_upload
import kotlin.time.Clock
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** `easystock-server-20261111-0830.easystock`, UTC like the local backups. */
internal fun serverBackupFileName(now: kotlin.time.Instant = Clock.System.now()): String {
    val stamp = now.toString().take(16).replace("-", "").replace(":", "").replace("T", "-")
    return "easystock-server-$stamp.$BACKUP_FILE_EXTENSION"
}

@Composable
internal fun ServerBackupScreen(api: AdminApi, onBack: () -> Unit) {
    var holder: ServerBackupViewModel? = null
    val files = rememberServerFiles(
        onPicked = { holder?.picked(it) },
        onSaved = { holder?.saved() },
        onFailed = { holder?.fileFailed() },
    )
    val viewModel = viewModel(key = "admin-backup") { ServerBackupViewModel(api, files, ::serverBackupFileName) }
    holder = viewModel
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    LaunchedEffect(state.message, state.failure, state.fileFailed) {
        val text = when {
            state.failure != null -> getString(state.failure!!.message())
            state.fileFailed -> getString(Res.string.admin_error_file)
            state.message == ServerBackupMessage.DOWNLOADED -> getString(Res.string.admin_backup_downloaded)
            state.message == ServerBackupMessage.IMPORTED -> getString(Res.string.admin_backup_imported)
            state.message == ServerBackupMessage.SAVED -> getString(Res.string.admin_backup_saved)
            else -> null
        }
        if (text != null) {
            viewModel.consumeMessage()
            snackbar.showSnackbar(text)
        }
    }

    val word = stringResource(Res.string.admin_backup_replace_word)
    Box(Modifier.fillMaxSize()) {
        ServerBackupContent(
            state = state,
            onBack = onBack,
            onDownload = viewModel::download,
            onSave = { state.downloaded?.let(files::save) },
            onShare = { state.downloaded?.let(files::share) },
            onPick = files::pick,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    state.import?.let { form ->
        ImportDialog(
            form = form,
            word = word,
            importing = state.importing,
            onMode = viewModel::chooseMode,
            onTyped = viewModel::typeConfirmation,
            onConfirm = { viewModel.import(word) },
            onDismiss = viewModel::closeImport,
        )
    }
}

@Composable
internal fun ServerBackupContent(
    state: ServerBackupState,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPick: () -> Unit,
) {
    SettingsSubpage(title = stringResource(Res.string.admin_backup), onBack = onBack) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.admin_backup_download), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.admin_backup_download_summary), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDownload, enabled = !state.busy) {
                Icon(painterResource(Res.drawable.ic_download), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(Res.string.admin_backup_download))
            }
            state.downloaded?.let { file ->
                Text(file.name, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSave, enabled = !state.busy) { Text(stringResource(Res.string.admin_backup_save)) }
                    OutlinedButton(onClick = onShare, enabled = !state.busy) { Text(stringResource(Res.string.admin_backup_share)) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(Res.string.admin_backup_import), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.admin_backup_import_summary), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onPick, enabled = !state.busy) {
                Icon(painterResource(Res.drawable.ic_upload), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(Res.string.admin_backup_import))
            }
        }
    }
}

/** Merge or replace. Replace erases the server data: the Admin types the localized word. */
@Composable
internal fun ImportDialog(
    form: ServerImportForm,
    word: String,
    importing: Boolean,
    onMode: (ServerImportMode) -> Unit,
    onTyped: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(stringResource(Res.string.admin_backup_import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(form.file.name, style = MaterialTheme.typography.bodySmall)
                ModeRow(ServerImportMode.MERGE, form.mode, Res.string.admin_backup_merge, Res.string.admin_backup_merge_summary, !importing, onMode)
                ModeRow(ServerImportMode.REPLACE, form.mode, Res.string.admin_backup_replace, Res.string.admin_backup_replace_summary, !importing, onMode)
                if (form.mode == ServerImportMode.REPLACE) {
                    OutlinedTextField(
                        value = form.typed,
                        onValueChange = onTyped,
                        label = { Text(stringResource(Res.string.admin_backup_replace_prompt, word)) },
                        singleLine = true,
                        enabled = !importing,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val replace = form.mode == ServerImportMode.REPLACE
            TextButton(
                onClick = onConfirm,
                enabled = !importing && form.canImport(word),
                colors = if (replace) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
            ) { Text(stringResource(if (replace) Res.string.admin_backup_replace else Res.string.admin_backup_import_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !importing) { Text(stringResource(Res.string.admin_cancel)) } },
    )
}

@Composable
private fun ModeRow(
    mode: ServerImportMode,
    selected: ServerImportMode,
    title: org.jetbrains.compose.resources.StringResource,
    summary: org.jetbrains.compose.resources.StringResource,
    enabled: Boolean,
    onMode: (ServerImportMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = mode == selected, enabled = enabled, role = SemanticsRole.RadioButton, onClick = { onMode(mode) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val previewFile = LocalFile("/cache/server/share/b.easystock", "easystock-server-20261111-0830.easystock", 2048, ServerBackupViewModel.MIME)

@Preview(showBackground = true)
@Composable
private fun ServerBackupPreview() {
    EasyStockTheme { ServerBackupContent(ServerBackupState(downloaded = previewFile), {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun ImportDialogPreview() {
    EasyStockTheme {
        ImportDialog(ServerImportForm(previewFile, ServerImportMode.REPLACE, "REPL"), "REPLACE", false, {}, {}, {}, {})
    }
}
