package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.backup.QrCodeImage
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.JoinKind
import ch.mathorck.easystock.server.PublicInvite
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.rememberServerFiles
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.admin.Loaded
import ch.mathorck.easystock.viewmodels.admin.PublicInviteViewModel
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_cancel
import easystock.shared.generated.resources.admin_error_file
import easystock.shared.generated.resources.admin_public_enabled
import easystock.shared.generated.resources.admin_public_enabled_summary
import easystock.shared.generated.resources.admin_public_guests
import easystock.shared.generated.resources.admin_public_invite
import easystock.shared.generated.resources.admin_public_limit
import easystock.shared.generated.resources.admin_public_limit_hint
import easystock.shared.generated.resources.admin_public_limit_none
import easystock.shared.generated.resources.admin_public_limit_save
import easystock.shared.generated.resources.admin_public_no_qr
import easystock.shared.generated.resources.admin_public_pdf
import easystock.shared.generated.resources.admin_public_pdf_line
import easystock.shared.generated.resources.admin_public_qr_title
import easystock.shared.generated.resources.admin_public_regenerate
import easystock.shared.generated.resources.admin_public_regenerate_message
import easystock.shared.generated.resources.admin_public_regenerate_title
import easystock.shared.generated.resources.admin_public_regenerated
import easystock.shared.generated.resources.admin_public_show
import easystock.shared.generated.resources.ic_print
import easystock.shared.generated.resources.ic_warning
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PublicInviteScreen(api: AdminApi, connection: ServerConnection, onBack: () -> Unit) {
    val viewModel = viewModel(key = "admin-public-${api.hashCode()}") { PublicInviteViewModel(api) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var fileFailed by remember { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    val files = rememberServerFiles(onPicked = {}, onSaved = {}, onFailed = { fileFailed = true })
    LaunchedEffect(Unit) { viewModel.refresh() }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    LaunchedEffect(state.failure, state.regenerated, fileFailed) {
        val text = when {
            state.failure != null -> getString(state.failure!!.message())
            state.regenerated -> getString(Res.string.admin_public_regenerated)
            fileFailed -> getString(Res.string.admin_error_file)
            else -> null
        }
        if (text != null) {
            fileFailed = false
            viewModel.consumeFailure()
            snackbar.showSnackbar(text)
        }
    }

    val qrText = state.grant?.qrText(connection, JoinKind.GUEST)
    val serverName = state.serverName ?: connection.serverUrl
    Box(Modifier.fillMaxSize()) {
        PublicInviteContent(
            invite = state.invite,
            busy = state.busy,
            qrText = qrText,
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onEnabled = viewModel::setEnabled,
            onLimit = viewModel::setGuestLimit,
            onRegenerate = viewModel::regenerate,
            onShowQr = { showQr = true },
            onPdf = {
                val text = qrText ?: return@PublicInviteContent
                scope.launch {
                    try {
                        val line = getString(Res.string.admin_public_pdf_line)
                        files.share(files.invitePdf("easystock-qr.pdf", text, serverName, line))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        fileFailed = true
                    }
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (showQr && qrText != null) {
        FullScreenQr(
            text = qrText,
            title = serverName,
            subtitle = stringResource(Res.string.admin_public_pdf_line),
            onClose = { showQr = false },
        )
    }
}

@Composable
internal fun PublicInviteContent(
    invite: Loaded<PublicInvite>,
    busy: Boolean,
    qrText: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onLimit: (Int?) -> Unit,
    onRegenerate: () -> Unit,
    onShowQr: () -> Unit,
    onPdf: () -> Unit,
) {
    var confirmRegenerate by rememberSaveable { mutableStateOf(false) }
    SettingsSubpage(title = stringResource(Res.string.admin_public_invite), onBack = onBack) {
        LoadedContent(invite, onRefresh, onRetry, isEmpty = { false }, emptyText = "") { data ->
            ListItem(
                headlineContent = { Text(stringResource(Res.string.admin_public_enabled)) },
                supportingContent = { Text(stringResource(Res.string.admin_public_enabled_summary)) },
                trailingContent = { Switch(checked = data.enabled, onCheckedChange = onEnabled, enabled = !busy) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            Text(
                stringResource(Res.string.admin_public_guests, data.guestCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            GuestLimitField(data.guestLimit, busy, onLimit)
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (qrText != null && data.enabled) {
                    QrCodeImage(qrText, description = stringResource(Res.string.admin_public_qr_title), modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth())
                } else if (data.enabled) {
                    Text(stringResource(Res.string.admin_public_no_qr), style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowQr, enabled = qrText != null && data.enabled) { Text(stringResource(Res.string.admin_public_show)) }
                    Button(onClick = onPdf, enabled = qrText != null && data.enabled && !busy) {
                        Icon(painterResource(Res.drawable.ic_print), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(Res.string.admin_public_pdf))
                    }
                    OutlinedButton(
                        onClick = { confirmRegenerate = true },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.admin_public_regenerate)) }
                }
            }
        }
    }
    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            icon = { Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.admin_public_regenerate_title)) },
            text = { Text(stringResource(Res.string.admin_public_regenerate_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = false
                    onRegenerate()
                }) { Text(stringResource(Res.string.admin_public_regenerate), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text(stringResource(Res.string.admin_cancel)) } },
        )
    }
}

/** Most Guests at a time; empty = no limit. Saved with the button, not on each key. */
@Composable
private fun GuestLimitField(limit: Int?, busy: Boolean, onLimit: (Int?) -> Unit) {
    var text by rememberSaveable(limit) { mutableStateOf(limit?.toString().orEmpty()) }
    val parsed = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val valid = text.isBlank() || (parsed != null && parsed >= 0)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(Res.string.admin_public_limit)) },
            placeholder = { Text(stringResource(Res.string.admin_public_limit_none)) },
            supportingText = { Text(stringResource(Res.string.admin_public_limit_hint)) },
            singleLine = true,
            enabled = !busy,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { onLimit(parsed) },
            enabled = !busy && valid && parsed != limit,
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(Res.string.admin_public_limit_save)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun PublicInvitePreview() {
    EasyStockTheme {
        PublicInviteContent(
            Loaded(PublicInvite(enabled = true, guestLimit = 20, guestCount = 3), loading = false),
            busy = false,
            qrText = "easystock://join?u=https%3A%2F%2F192.168.1.10&k=guest&t=abc",
            onBack = {}, onRefresh = {}, onRetry = {}, onEnabled = {}, onLimit = {}, onRegenerate = {}, onShowQr = {}, onPdf = {},
        )
    }
}
