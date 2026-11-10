package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.backup.BackupError
import ch.mathorck.easystock.backup.BackupFiles
import ch.mathorck.easystock.backup.BackupTable
import ch.mathorck.easystock.backup.ConflictType
import ch.mathorck.easystock.backup.ExportedBackup
import ch.mathorck.easystock.backup.ImportMode
import ch.mathorck.easystock.backup.ImportSummary
import ch.mathorck.easystock.backup.MergeConflict
import ch.mathorck.easystock.backup.OpenedBackup
import ch.mathorck.easystock.backup.QrCodeImage
import ch.mathorck.easystock.backup.QrDecision
import ch.mathorck.easystock.backup.QrScanner
import ch.mathorck.easystock.backup.WifiState
import ch.mathorck.easystock.backup.rememberBackupFiles
import ch.mathorck.easystock.backup.rememberIsChangingConfigurations
import ch.mathorck.easystock.backup.rememberBackupService
import ch.mathorck.easystock.backup.rowCount
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.BackupStep
import ch.mathorck.easystock.viewmodels.BackupUiState
import ch.mathorck.easystock.viewmodels.BackupViewModel
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.backup_cancel
import easystock.shared.generated.resources.backup_conflict_attribute_key_kept
import easystock.shared.generated.resources.backup_conflict_attribute_merged
import easystock.shared.generated.resources.backup_conflict_barcode_cleared
import easystock.shared.generated.resources.backup_conflict_category_attribute_skipped
import easystock.shared.generated.resources.backup_conflict_category_renamed
import easystock.shared.generated.resources.backup_conflict_location_parent_kept
import easystock.shared.generated.resources.backup_conflict_movement_kept
import easystock.shared.generated.resources.backup_conflict_serial_renamed
import easystock.shared.generated.resources.backup_conflict_sku_renamed
import easystock.shared.generated.resources.backup_count
import easystock.shared.generated.resources.backup_create
import easystock.shared.generated.resources.backup_done_added
import easystock.shared.generated.resources.backup_done_conflicts
import easystock.shared.generated.resources.backup_done_merged
import easystock.shared.generated.resources.backup_done_nothing
import easystock.shared.generated.resources.backup_done_photos
import easystock.shared.generated.resources.backup_done_replaced
import easystock.shared.generated.resources.backup_done_title
import easystock.shared.generated.resources.backup_done_updated
import easystock.shared.generated.resources.backup_downloading
import easystock.shared.generated.resources.backup_error_damaged
import easystock.shared.generated.resources.backup_error_failed
import easystock.shared.generated.resources.backup_error_newer_version
import easystock.shared.generated.resources.backup_error_not_a_backup
import easystock.shared.generated.resources.backup_error_not_on_wifi
import easystock.shared.generated.resources.backup_error_password_required
import easystock.shared.generated.resources.backup_error_timeout
import easystock.shared.generated.resources.backup_error_title
import easystock.shared.generated.resources.backup_error_too_large
import easystock.shared.generated.resources.backup_error_unreachable
import easystock.shared.generated.resources.backup_error_wrong_password
import easystock.shared.generated.resources.backup_exporting
import easystock.shared.generated.resources.backup_finish
import easystock.shared.generated.resources.backup_merge
import easystock.shared.generated.resources.backup_merge_summary
import easystock.shared.generated.resources.backup_ok
import easystock.shared.generated.resources.backup_open_button
import easystock.shared.generated.resources.backup_password
import easystock.shared.generated.resources.backup_password_confirm
import easystock.shared.generated.resources.backup_password_message
import easystock.shared.generated.resources.backup_password_mismatch
import easystock.shared.generated.resources.backup_password_title
import easystock.shared.generated.resources.backup_password_warning
import easystock.shared.generated.resources.backup_preview_created
import easystock.shared.generated.resources.backup_preview_empty
import easystock.shared.generated.resources.backup_preview_title
import easystock.shared.generated.resources.backup_privacy
import easystock.shared.generated.resources.backup_qr_description
import easystock.shared.generated.resources.backup_qr_message
import easystock.shared.generated.resources.backup_qr_password_hint
import easystock.shared.generated.resources.backup_qr_title
import easystock.shared.generated.resources.backup_qr_too_large
import easystock.shared.generated.resources.backup_ready_encrypted
import easystock.shared.generated.resources.backup_ready_plain
import easystock.shared.generated.resources.backup_ready_size
import easystock.shared.generated.resources.backup_ready_title
import easystock.shared.generated.resources.backup_receive_summary
import easystock.shared.generated.resources.backup_receive_title
import easystock.shared.generated.resources.backup_replace
import easystock.shared.generated.resources.backup_replace_confirm
import easystock.shared.generated.resources.backup_replace_confirm_message
import easystock.shared.generated.resources.backup_replace_confirm_title
import easystock.shared.generated.resources.backup_replace_summary
import easystock.shared.generated.resources.backup_save
import easystock.shared.generated.resources.backup_save_summary
import easystock.shared.generated.resources.backup_saved
import easystock.shared.generated.resources.backup_scan_button
import easystock.shared.generated.resources.backup_scan_hint
import easystock.shared.generated.resources.backup_scan_title
import easystock.shared.generated.resources.backup_send_button
import easystock.shared.generated.resources.backup_send_summary
import easystock.shared.generated.resources.backup_send_title
import easystock.shared.generated.resources.backup_share
import easystock.shared.generated.resources.backup_share_summary
import easystock.shared.generated.resources.backup_table_article
import easystock.shared.generated.resources.backup_table_attribute_definition
import easystock.shared.generated.resources.backup_table_category
import easystock.shared.generated.resources.backup_table_category_attribute
import easystock.shared.generated.resources.backup_table_item
import easystock.shared.generated.resources.backup_table_location
import easystock.shared.generated.resources.backup_table_photos
import easystock.shared.generated.resources.backup_table_stock_movement
import easystock.shared.generated.resources.backup_table_stock_review
import easystock.shared.generated.resources.backup_table_stock_review_line
import easystock.shared.generated.resources.backup_title
import easystock.shared.generated.resources.backup_unlock
import easystock.shared.generated.resources.backup_unlock_message
import easystock.shared.generated.resources.backup_unlock_title
import easystock.shared.generated.resources.backup_unlock_wrong
import easystock.shared.generated.resources.backup_via_qr
import easystock.shared.generated.resources.backup_via_qr_summary
import easystock.shared.generated.resources.backup_via_wifi
import easystock.shared.generated.resources.backup_via_wifi_summary
import easystock.shared.generated.resources.backup_wifi_blocked
import easystock.shared.generated.resources.backup_wifi_done
import easystock.shared.generated.resources.backup_wifi_expired
import easystock.shared.generated.resources.backup_wifi_qr_description
import easystock.shared.generated.resources.backup_wifi_restart
import easystock.shared.generated.resources.backup_wifi_sending
import easystock.shared.generated.resources.backup_wifi_stop
import easystock.shared.generated.resources.backup_wifi_title
import easystock.shared.generated.resources.backup_wifi_waiting
import easystock.shared.generated.resources.backup_working
import easystock.shared.generated.resources.ic_backup
import easystock.shared.generated.resources.ic_download
import easystock.shared.generated.resources.ic_folder_open
import easystock.shared.generated.resources.ic_lock
import easystock.shared.generated.resources.ic_qr_code
import easystock.shared.generated.resources.ic_qr_code_scanner
import easystock.shared.generated.resources.ic_save
import easystock.shared.generated.resources.ic_share
import easystock.shared.generated.resources.ic_upload
import easystock.shared.generated.resources.ic_warning
import easystock.shared.generated.resources.ic_wifi
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Backup and transfer. Send (QR code, Wi-Fi, file) or receive (scan, file) all data.
 * [onBack] runs on the arrow and the system back, once the screen is back at its start.
 */
@Composable
fun BackupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val service = rememberBackupService()
    val viewModel = viewModel(key = "settings-backup") { BackupViewModel(service) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var saved by remember { mutableStateOf(false) }
    val files = rememberBackupFiles(
        onOpened = viewModel::fileOpened,
        onSaved = { saved = true },
        onError = viewModel::reportError,
    )
    // The view model outlives the screen (it belongs to the activity): leaving must not keep a result
    val isChangingConfigurations = rememberIsChangingConfigurations()
    DisposableEffect(viewModel) {
        viewModel.enter()
        onDispose { if (!isChangingConfigurations()) viewModel.leave() }
    }
    val back: () -> Unit = {
        saved = false
        when (state.step) {
            BackupStep.Home -> {
                viewModel.reset()
                onBack()
            }
            is BackupStep.ShowingQr, is BackupStep.SendingWifi -> viewModel.backToExport()
            // An import is being written: it must finish (one transaction, then the photos)
            BackupStep.Working -> Unit
            else -> viewModel.reset()
        }
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = back)

    BackupContent(
        state = state,
        saved = saved,
        files = files,
        onBack = back,
        onExport = { saved = false; viewModel.export(it) },
        onShowQr = viewModel::showQr,
        onStartWifi = viewModel::startWifi,
        onScan = viewModel::scan,
        onScanned = viewModel::scanned,
        onPassword = viewModel::submitPassword,
        onImport = viewModel::import,
        onFinish = { saved = false; viewModel.reset() },
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

/** Stateless content, previewable. */
@Composable
fun BackupContent(
    state: BackupUiState,
    saved: Boolean,
    files: BackupFiles?,
    onBack: () -> Unit,
    onExport: (password: String) -> Unit,
    onShowQr: () -> Unit,
    onStartWifi: () -> Unit,
    onScan: () -> Unit,
    onScanned: (String) -> Unit,
    onPassword: (String) -> Unit,
    onImport: (ImportMode) -> Unit,
    onFinish: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var askPassword by remember { mutableStateOf(false) }
    SettingsSubpage(title = stringResource(Res.string.backup_title), onBack = onBack, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val step = state.step) {
                BackupStep.Home -> HomeStep(onCreate = { askPassword = true }, onScan = onScan, onOpen = { files?.open() })
                BackupStep.Exporting -> Busy(stringResource(Res.string.backup_exporting))
                is BackupStep.Exported -> ExportedStep(step.export, saved, files, onShowQr, onStartWifi, onFinish)
                is BackupStep.ShowingQr -> QrStep(step.export, step.text, onBack)
                is BackupStep.SendingWifi -> WifiStep(step.qrText, step.state, onStartWifi, onBack)
                BackupStep.Scanning -> ScanStep(onScanned, onOpen = { files?.open() })
                is BackupStep.Downloading ->
                    Busy(stringResource(Res.string.backup_downloading, formatBytes(step.receivedBytes)))
                is BackupStep.PasswordNeeded -> {
                    Busy(stringResource(Res.string.backup_working))
                    UnlockDialog(wrong = step.wrong, onSubmit = onPassword, onDismiss = onFinish)
                }
                BackupStep.Working -> Busy(stringResource(Res.string.backup_working))
                is BackupStep.Preview -> PreviewStep(step.backup, onImport, onFinish)
                is BackupStep.Done -> DoneStep(step.summary, onFinish)
            }
        }
    }
    if (askPassword) {
        ExportPasswordDialog(
            onCreate = {
                askPassword = false
                onExport(it)
            },
            onDismiss = { askPassword = false },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            icon = { Icon(painterResource(Res.drawable.ic_warning), contentDescription = null) },
            title = { Text(stringResource(Res.string.backup_error_title)) },
            text = { Text(stringResource(error.message())) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(Res.string.backup_ok)) } },
        )
    }
}

// ---------- Steps ----------

@Composable
private fun HomeStep(onCreate: () -> Unit, onScan: () -> Unit, onOpen: () -> Unit) {
    Text(
        stringResource(Res.string.backup_privacy),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Section(Res.drawable.ic_upload, stringResource(Res.string.backup_send_title), stringResource(Res.string.backup_send_summary)) {
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(Res.drawable.ic_backup)
            Text(stringResource(Res.string.backup_send_button))
        }
    }
    Section(Res.drawable.ic_download, stringResource(Res.string.backup_receive_title), stringResource(Res.string.backup_receive_summary)) {
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(Res.drawable.ic_qr_code_scanner)
            Text(stringResource(Res.string.backup_scan_button))
        }
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(Res.drawable.ic_folder_open)
            Text(stringResource(Res.string.backup_open_button))
        }
    }
}

@Composable
private fun ColumnScope.ExportedStep(
    export: ExportedBackup,
    saved: Boolean,
    files: BackupFiles?,
    onShowQr: () -> Unit,
    onStartWifi: () -> Unit,
    onFinish: () -> Unit,
) {
    Section(Res.drawable.ic_backup, stringResource(Res.string.backup_ready_title), stringResource(Res.string.backup_ready_size, formatBytes(export.sizeBytes), export.photos)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (export.encrypted) Icon(painterResource(Res.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (export.encrypted) Res.string.backup_ready_encrypted else Res.string.backup_ready_plain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val fits = export.qr is QrDecision.Fits
    Card {
        Option(
            icon = Res.drawable.ic_qr_code,
            title = stringResource(Res.string.backup_via_qr),
            summary = stringResource(if (fits) Res.string.backup_via_qr_summary else Res.string.backup_qr_too_large),
            enabled = fits,
            onClick = onShowQr,
        )
        HorizontalDivider()
        Option(Res.drawable.ic_wifi, stringResource(Res.string.backup_via_wifi), stringResource(Res.string.backup_via_wifi_summary), onClick = onStartWifi)
        HorizontalDivider()
        Option(Res.drawable.ic_save, stringResource(Res.string.backup_save), stringResource(Res.string.backup_save_summary), onClick = { files?.save(export) })
        HorizontalDivider()
        Option(Res.drawable.ic_share, stringResource(Res.string.backup_share), stringResource(Res.string.backup_share_summary), onClick = { files?.share(export) })
    }
    if (saved) Text(stringResource(Res.string.backup_saved), color = MaterialTheme.colorScheme.primary)
    TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.backup_finish)) }
}

@Composable
private fun ColumnScope.QrStep(export: ExportedBackup, text: String, onBack: () -> Unit) {
    Text(stringResource(Res.string.backup_qr_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.backup_qr_message), style = MaterialTheme.typography.bodyMedium)
    if (export.encrypted) Text(stringResource(Res.string.backup_qr_password_hint), style = MaterialTheme.typography.bodyMedium)
    QrCodeImage(text, stringResource(Res.string.backup_qr_description), Modifier.fillMaxWidth().widthIn(max = 480.dp).align(Alignment.CenterHorizontally))
    OutlinedButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.backup_finish)) }
}

@Composable
private fun ColumnScope.WifiStep(qrText: String, state: WifiState, onRestart: () -> Unit, onStop: () -> Unit) {
    Text(stringResource(Res.string.backup_wifi_title), style = MaterialTheme.typography.titleMedium)
    when (state) {
        WifiState.Waiting -> {
            Text(stringResource(Res.string.backup_wifi_waiting), style = MaterialTheme.typography.bodyMedium)
            QrCodeImage(qrText, stringResource(Res.string.backup_wifi_qr_description), Modifier.fillMaxWidth().widthIn(max = 480.dp).align(Alignment.CenterHorizontally))
        }
        is WifiState.Sending -> {
            Text(stringResource(Res.string.backup_wifi_sending, formatBytes(state.sentBytes), formatBytes(state.totalBytes)))
            LinearProgressIndicator(
                progress = { if (state.totalBytes > 0) state.sentBytes.toFloat() / state.totalBytes else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        WifiState.Done -> Text(stringResource(Res.string.backup_wifi_done), color = MaterialTheme.colorScheme.primary)
        WifiState.Expired -> Text(stringResource(Res.string.backup_wifi_expired))
        WifiState.Blocked -> Text(stringResource(Res.string.backup_wifi_blocked), color = MaterialTheme.colorScheme.error)
        is WifiState.Failed -> Text(stringResource(state.error.message()), color = MaterialTheme.colorScheme.error)
    }
    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val finished = state !is WifiState.Waiting && state !is WifiState.Sending
        if (finished && state != WifiState.Done) {
            OutlinedButton(onClick = onRestart) { Text(stringResource(Res.string.backup_wifi_restart)) }
        }
        Button(onClick = onStop) {
            Text(stringResource(if (finished) Res.string.backup_finish else Res.string.backup_wifi_stop))
        }
    }
}

@Composable
private fun ColumnScope.ScanStep(onScanned: (String) -> Unit, onOpen: () -> Unit) {
    Text(stringResource(Res.string.backup_scan_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.backup_scan_hint), style = MaterialTheme.typography.bodyMedium)
    QrScanner(
        onScanned = onScanned,
        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)),
    )
    OutlinedButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
        ButtonIcon(Res.drawable.ic_folder_open)
        Text(stringResource(Res.string.backup_open_button))
    }
}

@Composable
private fun PreviewStep(backup: OpenedBackup, onImport: (ImportMode) -> Unit, onCancel: () -> Unit) {
    var confirmReplace by remember { mutableStateOf(false) }
    Section(Res.drawable.ic_backup, stringResource(Res.string.backup_preview_title), stringResource(Res.string.backup_preview_created, formatCreatedAt(backup.manifest.createdAt))) {
        val counts = BackupTable.entries.map { it to backup.data.rowCount(it) }.filter { it.second > 0 }
        if (counts.isEmpty() && backup.photos == 0) {
            Text(stringResource(Res.string.backup_preview_empty), style = MaterialTheme.typography.bodyMedium)
        }
        counts.forEach { (table, count) -> CountLine(stringResource(table.label()), count) }
        if (backup.photos > 0) CountLine(stringResource(Res.string.backup_table_photos), backup.photos)
    }
    Card {
        Option(Res.drawable.ic_download, stringResource(Res.string.backup_merge), stringResource(Res.string.backup_merge_summary), onClick = { onImport(ImportMode.MERGE) })
        HorizontalDivider()
        Option(Res.drawable.ic_warning, stringResource(Res.string.backup_replace), stringResource(Res.string.backup_replace_summary), onClick = { confirmReplace = true })
    }
    TextButton(onClick = onCancel) { Text(stringResource(Res.string.backup_cancel)) }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            icon = { Icon(painterResource(Res.drawable.ic_warning), contentDescription = null) },
            title = { Text(stringResource(Res.string.backup_replace_confirm_title)) },
            text = { Text(stringResource(Res.string.backup_replace_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    onImport(ImportMode.REPLACE)
                }) { Text(stringResource(Res.string.backup_replace_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(Res.string.backup_cancel)) } },
        )
    }
}

@Composable
private fun ColumnScope.DoneStep(summary: ImportSummary, onFinish: () -> Unit) {
    val message = if (summary.mode == ImportMode.REPLACE) Res.string.backup_done_replaced else Res.string.backup_done_merged
    Section(Res.drawable.ic_backup, stringResource(Res.string.backup_done_title), stringResource(message)) {
        if (summary.totalAdded == 0 && summary.totalUpdated == 0 && summary.photos == 0) {
            Text(stringResource(Res.string.backup_done_nothing), style = MaterialTheme.typography.bodyMedium)
        }
        CountGroup(stringResource(Res.string.backup_done_added), summary.added)
        CountGroup(stringResource(Res.string.backup_done_updated), summary.updated)
        if (summary.photos > 0) Text(stringResource(Res.string.backup_done_photos, summary.photos), style = MaterialTheme.typography.bodyMedium)
    }
    if (summary.conflicts.isNotEmpty()) {
        Section(Res.drawable.ic_warning, stringResource(Res.string.backup_done_conflicts), summary.conflicts.size.toString()) {
            summary.conflicts.forEach { Text("• " + it.describe(), style = MaterialTheme.typography.bodyMedium) }
        }
    }
    Button(onClick = onFinish, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.backup_finish)) }
}

// ---------- Dialogs ----------

@Composable
private fun ExportPasswordDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    // Not saveable: a password must not end up in the saved instance state
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = password.isNotEmpty() && confirm.isNotEmpty() && password != confirm
    val valid = password.isEmpty() || password == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_lock), contentDescription = null) },
        title = { Text(stringResource(Res.string.backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.backup_password_message))
                PasswordField(password, { password = it }, stringResource(Res.string.backup_password))
                if (password.isNotEmpty()) {
                    PasswordField(
                        confirm,
                        { confirm = it },
                        stringResource(Res.string.backup_password_confirm),
                        error = if (mismatch) stringResource(Res.string.backup_password_mismatch) else null,
                    )
                    Text(
                        stringResource(Res.string.backup_password_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(password) }, enabled = valid) { Text(stringResource(Res.string.backup_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.backup_cancel)) } },
    )
}

@Composable
private fun UnlockDialog(wrong: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember(wrong) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_lock), contentDescription = null) },
        title = { Text(stringResource(Res.string.backup_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.backup_unlock_message))
                PasswordField(
                    password,
                    { password = it },
                    stringResource(Res.string.backup_password),
                    error = if (wrong) stringResource(Res.string.backup_unlock_wrong) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(stringResource(Res.string.backup_unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.backup_cancel)) } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Building blocks ----------

@Composable
private fun Section(icon: DrawableResource, title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Option(icon: DrawableResource, title: String, summary: String, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.5f
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = { Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun ButtonIcon(icon: DrawableResource) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
}

@Composable
private fun Busy(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CountLine(label: String, count: Int) {
    Text(stringResource(Res.string.backup_count, label, count), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CountGroup(title: String, counts: Map<BackupTable, Int>) {
    if (counts.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    BackupTable.entries.forEach { table -> counts[table]?.let { CountLine(stringResource(table.label()), it) } }
}

// ---------- Texts ----------

private fun BackupTable.label(): StringResource = when (this) {
    BackupTable.CATEGORY -> Res.string.backup_table_category
    BackupTable.ATTRIBUTE_DEFINITION -> Res.string.backup_table_attribute_definition
    BackupTable.CATEGORY_ATTRIBUTE -> Res.string.backup_table_category_attribute
    BackupTable.LOCATION -> Res.string.backup_table_location
    BackupTable.ARTICLE -> Res.string.backup_table_article
    BackupTable.ITEM -> Res.string.backup_table_item
    BackupTable.STOCK_MOVEMENT -> Res.string.backup_table_stock_movement
    BackupTable.STOCK_REVIEW -> Res.string.backup_table_stock_review
    BackupTable.STOCK_REVIEW_LINE -> Res.string.backup_table_stock_review_line
}

internal fun BackupError.message(): StringResource = when (this) {
    BackupError.NOT_A_BACKUP -> Res.string.backup_error_not_a_backup
    BackupError.NEWER_VERSION -> Res.string.backup_error_newer_version
    BackupError.DAMAGED -> Res.string.backup_error_damaged
    BackupError.PASSWORD_REQUIRED -> Res.string.backup_error_password_required
    BackupError.WRONG_PASSWORD -> Res.string.backup_error_wrong_password
    BackupError.TOO_LARGE -> Res.string.backup_error_too_large
    BackupError.NOT_ON_WIFI -> Res.string.backup_error_not_on_wifi
    BackupError.UNREACHABLE -> Res.string.backup_error_unreachable
    BackupError.TIMEOUT -> Res.string.backup_error_timeout
    BackupError.FAILED -> Res.string.backup_error_failed
}

@Composable
private fun MergeConflict.describe(): String {
    val res = when (type) {
        ConflictType.CATEGORY_RENAMED -> Res.string.backup_conflict_category_renamed
        ConflictType.SKU_RENAMED -> Res.string.backup_conflict_sku_renamed
        ConflictType.BARCODE_CLEARED -> Res.string.backup_conflict_barcode_cleared
        ConflictType.SERIAL_RENAMED -> Res.string.backup_conflict_serial_renamed
        ConflictType.ATTRIBUTE_MERGED -> Res.string.backup_conflict_attribute_merged
        ConflictType.ATTRIBUTE_KEY_KEPT -> Res.string.backup_conflict_attribute_key_kept
        ConflictType.CATEGORY_ATTRIBUTE_SKIPPED -> Res.string.backup_conflict_category_attribute_skipped
        ConflictType.LOCATION_PARENT_KEPT -> Res.string.backup_conflict_location_parent_kept
        ConflictType.MOVEMENT_KEPT -> Res.string.backup_conflict_movement_kept
    }
    return stringResource(res, value.orEmpty(), resolved.orEmpty())
}

/** "1.4 MB", "820 KB", "12 B". */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
    else -> {
        val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
        "${tenths / 10}.${tenths % 10} MB"
    }
}

/** "2026-11-10T14:03:00Z" -> "2026-11-10 14:03". Anything else is shown as is. */
internal fun formatCreatedAt(iso: String): String {
    val match = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}).*""").find(iso) ?: return iso
    return "${match.groupValues[1]} ${match.groupValues[2]}"
}

@Preview(showBackground = true)
@Composable
private fun BackupHomePreview() {
    EasyStockTheme {
        BackupContent(
            state = BackupUiState(),
            saved = false,
            files = null,
            onBack = {}, onExport = {}, onShowQr = {}, onStartWifi = {}, onScan = {}, onScanned = {},
            onPassword = {}, onImport = {}, onFinish = {}, onDismissError = {},
        )
    }
}
