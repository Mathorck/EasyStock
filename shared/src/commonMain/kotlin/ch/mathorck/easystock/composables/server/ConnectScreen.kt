package ch.mathorck.easystock.composables.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.backup.QrScanner
import ch.mathorck.easystock.composables.settings.ResetDataDialog
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.ServerInfo
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.server.ConnectStep
import ch.mathorck.easystock.viewmodels.server.ConnectUiState
import ch.mathorck.easystock.viewmodels.server.ConnectViewModel
import ch.mathorck.easystock.viewmodels.server.FormField
import ch.mathorck.easystock.viewmodels.server.FormMode
import ch.mathorck.easystock.viewmodels.server.fields
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_dns
import easystock.shared.generated.resources.ic_qr_code_scanner
import easystock.shared.generated.resources.ic_visibility
import easystock.shared.generated.resources.ic_visibility_off
import easystock.shared.generated.resources.ic_warning
import easystock.shared.generated.resources.server_cancel
import easystock.shared.generated.resources.server_confirm_code_message
import easystock.shared.generated.resources.server_confirm_code_title
import easystock.shared.generated.resources.server_connect_button
import easystock.shared.generated.resources.server_checking
import easystock.shared.generated.resources.server_connect_address
import easystock.shared.generated.resources.server_connect_address_hint
import easystock.shared.generated.resources.server_connect_address_invalid
import easystock.shared.generated.resources.server_connect_intro
import easystock.shared.generated.resources.server_connect_next
import easystock.shared.generated.resources.server_connect_or
import easystock.shared.generated.resources.server_connect_scan
import easystock.shared.generated.resources.server_connect_title
import easystock.shared.generated.resources.server_error_title
import easystock.shared.generated.resources.server_export_then_erase
import easystock.shared.generated.resources.server_field_display_name
import easystock.shared.generated.resources.server_field_display_name_rule
import easystock.shared.generated.resources.server_field_new_password
import easystock.shared.generated.resources.server_field_password
import easystock.shared.generated.resources.server_field_password_hide
import easystock.shared.generated.resources.server_field_password_rule
import easystock.shared.generated.resources.server_field_password_show
import easystock.shared.generated.resources.server_field_required
import easystock.shared.generated.resources.server_field_username
import easystock.shared.generated.resources.server_field_username_rule
import easystock.shared.generated.resources.server_form_guest_message
import easystock.shared.generated.resources.server_form_guest_title
import easystock.shared.generated.resources.server_form_invite_title
import easystock.shared.generated.resources.server_form_login_title
import easystock.shared.generated.resources.server_form_reset_message
import easystock.shared.generated.resources.server_form_reset_title
import easystock.shared.generated.resources.server_form_setup_message
import easystock.shared.generated.resources.server_form_setup_title
import easystock.shared.generated.resources.server_form_submit_create
import easystock.shared.generated.resources.server_form_submit_guest
import easystock.shared.generated.resources.server_form_submit_login
import easystock.shared.generated.resources.server_form_submit_reset
import easystock.shared.generated.resources.server_guest_continue
import easystock.shared.generated.resources.server_guest_login
import easystock.shared.generated.resources.server_guest_message
import easystock.shared.generated.resources.server_guest_off
import easystock.shared.generated.resources.server_guest_title
import easystock.shared.generated.resources.server_http_continue
import easystock.shared.generated.resources.server_http_warning_message
import easystock.shared.generated.resources.server_http_warning_title
import easystock.shared.generated.resources.server_https_failed_message
import easystock.shared.generated.resources.server_https_failed_title
import easystock.shared.generated.resources.server_not_empty_message
import easystock.shared.generated.resources.server_not_empty_title
import easystock.shared.generated.resources.server_ok
import easystock.shared.generated.resources.server_scan_hint
import easystock.shared.generated.resources.server_try_http
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The user's actions on the connect screen, so that the content can be previewed without a view model. */
internal class ConnectActions(
    val startScan: () -> Unit = {},
    val scanned: (String) -> Unit = {},
    val scanCancelled: () -> Unit = {},
    val addressChanged: (String) -> Unit = {},
    val submitAddress: () -> Unit = {},
    val cancel: () -> Unit = {},
    val tryHttp: () -> Unit = {},
    val acceptHttp: () -> Unit = {},
    val confirmCode: () -> Unit = {},
    val askErase: () -> Unit = {},
    val cancelErase: () -> Unit = {},
    val confirmErase: () -> Unit = {},
    val chooseLogin: () -> Unit = {},
    val chooseGuest: () -> Unit = {},
    val usernameChanged: (String) -> Unit = {},
    val displayNameChanged: (String) -> Unit = {},
    val passwordChanged: (String) -> Unit = {},
    val submitForm: () -> Unit = {},
    val dismissError: () -> Unit = {},
)

/**
 * Settings › Server on a standalone phone. Nothing is sent before the user scans a code or submits an
 * address; nothing is written before the join call succeeded ([ConnectStep.Done]).
 */
@Composable
fun ConnectScreen(vm: ConnectViewModel, onBack: () -> Unit, onOpenBackup: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = ConnectActions(
        startScan = vm::startScan,
        scanned = vm::scanned,
        scanCancelled = vm::scanCancelled,
        addressChanged = vm::addressChanged,
        submitAddress = vm::submitAddress,
        cancel = vm::cancel,
        tryHttp = vm::tryHttp,
        acceptHttp = vm::acceptHttp,
        confirmCode = vm::confirmCode,
        askErase = vm::askErase,
        cancelErase = vm::cancelErase,
        confirmErase = vm::confirmErase,
        chooseLogin = vm::chooseLogin,
        chooseGuest = vm::chooseGuest,
        usernameChanged = vm::usernameChanged,
        displayNameChanged = vm::displayNameChanged,
        passwordChanged = vm::passwordChanged,
        submitForm = vm::submitForm,
        dismissError = vm::dismissError,
    )
    val step = state.step
    // A running erase or join call is not interrupted by back
    val busy = step is ConnectStep.Erasing || (step as? ConnectStep.Form)?.busy == true || step == ConnectStep.Done
    val back = {
        when {
            busy -> Unit
            step == ConnectStep.Choose -> onBack()
            step == ConnectStep.Scanning -> vm.scanCancelled()
            else -> vm.cancel()
        }
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = back)
    ConnectContent(
        state = state,
        actions = actions,
        onBack = back,
        // The erase dialog closes: back on the screen, the export is one tap away
        onOpenBackup = {
            vm.cancelErase()
            onOpenBackup()
        },
    )
}

@Composable
internal fun ConnectContent(state: ConnectUiState, actions: ConnectActions, onBack: () -> Unit, onOpenBackup: () -> Unit) {
    SettingsSubpage(title = stringResource(Res.string.server_connect_title), onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val step = state.step) {
                ConnectStep.Choose -> ChooseStep(state, actions)
                ConnectStep.Scanning -> ScanStep(actions)
                is ConnectStep.Checking -> CheckingStep(step.target.connection, actions.cancel)
                // The dialog below asks; behind it, the server being reached
                is ConnectStep.HttpsFailed -> CheckingStep(step.target.connection, actions.cancel, running = false)
                is ConnectStep.HttpWarning -> CheckingStep(step.target.connection, actions.cancel, running = false)
                is ConnectStep.ConfirmCode -> CheckingStep(step.target.connection, actions.cancel, running = false)
                is ConnectStep.NotEmpty -> NotEmptyStep(step.target.connection, step.info, actions)
                is ConnectStep.EraseConfirm -> NotEmptyStep(step.target.connection, step.info, actions)
                is ConnectStep.Erasing -> NotEmptyStep(step.target.connection, step.info, actions)
                is ConnectStep.GuestChoice -> GuestChoiceStep(step.target.connection, step.info, actions)
                is ConnectStep.Form -> FormStep(step, state, actions)
                ConnectStep.Done -> Spinner()
            }
        }
    }

    when (val step = state.step) {
        is ConnectStep.HttpsFailed -> AlertDialog(
            onDismissRequest = actions.cancel,
            title = { Text(stringResource(Res.string.server_https_failed_title)) },
            text = { Text(stringResource(Res.string.server_https_failed_message, step.target.connection.host())) },
            confirmButton = { TextButton(onClick = actions.tryHttp) { Text(stringResource(Res.string.server_try_http)) } },
            dismissButton = { TextButton(onClick = actions.cancel) { Text(stringResource(Res.string.server_cancel)) } },
        )
        is ConnectStep.ConfirmCode -> AlertDialog(
            onDismissRequest = actions.cancel,
            title = { Text(stringResource(Res.string.server_confirm_code_title)) },
            text = { Text(stringResource(Res.string.server_confirm_code_message, step.target.connection.host())) },
            confirmButton = { TextButton(onClick = actions.confirmCode) { Text(stringResource(Res.string.server_connect_button)) } },
            dismissButton = { TextButton(onClick = actions.cancel) { Text(stringResource(Res.string.server_cancel)) } },
        )
        is ConnectStep.HttpWarning -> AlertDialog(
            onDismissRequest = actions.cancel,
            icon = { Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.server_http_warning_title)) },
            text = { Text(stringResource(Res.string.server_http_warning_message, step.target.connection.host())) },
            confirmButton = {
                TextButton(
                    onClick = actions.acceptHttp,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.server_http_continue)) }
            },
            dismissButton = { TextButton(onClick = actions.cancel) { Text(stringResource(Res.string.server_cancel)) } },
        )
        is ConnectStep.EraseConfirm -> ResetDataDialog(
            running = false,
            failed = step.failed,
            onConfirm = actions.confirmErase,
            onOpenBackup = onOpenBackup,
            onDismiss = actions.cancelErase,
        )
        is ConnectStep.Erasing -> ResetDataDialog(running = true, failed = false, onConfirm = {}, onOpenBackup = {}, onDismiss = {})
        else -> Unit
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = actions.dismissError,
            title = { Text(stringResource(Res.string.server_error_title)) },
            text = { Text(stringResource(error.message())) },
            confirmButton = { TextButton(onClick = actions.dismissError) { Text(stringResource(Res.string.server_ok)) } },
        )
    }
}

@Composable
private fun ColumnScope.ChooseStep(state: ConnectUiState, actions: ConnectActions) {
    Text(stringResource(Res.string.server_connect_intro), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = actions.startScan, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(Res.drawable.ic_qr_code_scanner), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(Res.string.server_connect_scan))
    }
    Text(
        stringResource(Res.string.server_connect_or),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.address,
        onValueChange = actions.addressChanged,
        label = { Text(stringResource(Res.string.server_connect_address)) },
        singleLine = true,
        isError = state.addressInvalid,
        supportingText = {
            Text(stringResource(if (state.addressInvalid) Res.string.server_connect_address_invalid else Res.string.server_connect_address_hint))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (state.address.isNotBlank()) actions.submitAddress() }),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = actions.submitAddress,
        enabled = state.address.isNotBlank(),
        modifier = Modifier.align(Alignment.End),
    ) { Text(stringResource(Res.string.server_connect_next)) }
}

@Composable
private fun ScanStep(actions: ConnectActions) {
    Text(stringResource(Res.string.server_scan_hint), style = MaterialTheme.typography.bodyMedium)
    QrScanner(
        onScanned = actions.scanned,
        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)),
    )
    OutlinedButton(onClick = actions.scanCancelled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.server_cancel))
    }
}

/** The server being joined: its name when known, its address and how the connection is secured. */
@Composable
private fun ServerCard(connection: ServerConnection, info: ServerInfo?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(Res.drawable.ic_dns), contentDescription = null)
            Column(Modifier.padding(start = 16.dp)) {
                val name = info?.serverName?.takeIf { it.isNotBlank() }
                Text(name ?: connection.host(), style = MaterialTheme.typography.titleMedium)
                if (name != null) Text(connection.host(), style = MaterialTheme.typography.bodyMedium)
                ConnectionSecurity(connection, Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CheckingStep(connection: ServerConnection, onCancel: () -> Unit, running: Boolean = true) {
    ServerCard(connection, null)
    if (running) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(Res.string.server_checking), modifier = Modifier.padding(start = 16.dp))
        }
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.server_cancel)) }
    }
}

@Composable
private fun NotEmptyStep(connection: ServerConnection, info: ServerInfo, actions: ConnectActions) {
    ServerCard(connection, info)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            stringResource(Res.string.server_not_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    Text(stringResource(Res.string.server_not_empty_message), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = actions.askErase, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.server_export_then_erase))
    }
    OutlinedButton(onClick = actions.cancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.server_cancel))
    }
}

@Composable
private fun ColumnScope.GuestChoiceStep(connection: ServerConnection, info: ServerInfo, actions: ConnectActions) {
    ServerCard(connection, info)
    Text(
        stringResource(Res.string.server_guest_title, info.serverName.ifBlank { connection.host() }),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(stringResource(Res.string.server_guest_message), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = actions.chooseGuest, enabled = info.guestSignupEnabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.server_guest_continue))
    }
    if (!info.guestSignupEnabled) {
        Text(
            stringResource(Res.string.server_guest_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedButton(onClick = actions.chooseLogin, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.server_guest_login))
    }
    TextButton(onClick = actions.cancel, modifier = Modifier.align(Alignment.End)) {
        Text(stringResource(Res.string.server_cancel))
    }
}

@Composable
private fun ColumnScope.FormStep(step: ConnectStep.Form, state: ConnectUiState, actions: ConnectActions) {
    ServerCard(step.target.connection, step.info)
    val mode = step.mode
    Text(
        stringResource(
            when (mode) {
                FormMode.LOGIN -> Res.string.server_form_login_title
                FormMode.INVITE -> Res.string.server_form_invite_title
                FormMode.SETUP -> Res.string.server_form_setup_title
                FormMode.RESET -> Res.string.server_form_reset_title
                FormMode.GUEST -> Res.string.server_form_guest_title
            },
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    when (mode) {
        FormMode.SETUP -> Res.string.server_form_setup_message
        FormMode.RESET -> Res.string.server_form_reset_message
        FormMode.GUEST -> Res.string.server_form_guest_message
        else -> null
    }?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }

    val fields = mode.fields()
    val enabled = !step.busy
    // A login only checks that the fields are filled in: the rules are for new accounts
    val rules = mode != FormMode.LOGIN
    if (FormField.USERNAME in fields) {
        FormTextField(
            value = state.username,
            onValueChange = actions.usernameChanged,
            label = stringResource(Res.string.server_field_username),
            rule = if (rules) stringResource(Res.string.server_field_username_rule) else null,
            invalid = FormField.USERNAME in state.invalidFields,
            enabled = enabled,
        )
    }
    if (FormField.DISPLAY_NAME in fields) {
        FormTextField(
            value = state.displayName,
            onValueChange = actions.displayNameChanged,
            label = stringResource(Res.string.server_field_display_name),
            rule = stringResource(Res.string.server_field_display_name_rule),
            invalid = FormField.DISPLAY_NAME in state.invalidFields,
            enabled = enabled,
            keyboardType = KeyboardType.Text,
        )
    }
    if (FormField.PASSWORD in fields) {
        PasswordField(
            value = state.password,
            onValueChange = actions.passwordChanged,
            label = stringResource(if (mode == FormMode.RESET) Res.string.server_field_new_password else Res.string.server_field_password),
            rule = if (rules) stringResource(Res.string.server_field_password_rule) else null,
            invalid = FormField.PASSWORD in state.invalidFields,
            enabled = enabled,
            onDone = actions.submitForm,
        )
    }
    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = actions.cancel, enabled = enabled) { Text(stringResource(Res.string.server_cancel)) }
        Button(onClick = actions.submitForm, enabled = enabled) {
            if (step.busy) {
                CircularProgressIndicator(Modifier.size(18.dp).padding(end = 0.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(
                        when (mode) {
                            FormMode.LOGIN -> Res.string.server_form_submit_login
                            FormMode.INVITE, FormMode.SETUP -> Res.string.server_form_submit_create
                            FormMode.RESET -> Res.string.server_form_submit_reset
                            FormMode.GUEST -> Res.string.server_form_submit_guest
                        },
                    ),
                )
            }
        }
    }
}

/** A form field that shows its rule below, in red once a submit found it broken ([rule] null: only "required"). */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    rule: String?,
    invalid: Boolean,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Ascii,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = invalid,
        supportingText = (rule ?: if (invalid) stringResource(Res.string.server_field_required) else null)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A password with a show/hide toggle. Only the rule is shown, never a strength meter. */
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    rule: String?,
    invalid: Boolean,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = invalid,
        supportingText = (rule ?: if (invalid) stringResource(Res.string.server_field_required) else null)?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) Res.drawable.ic_visibility_off else Res.drawable.ic_visibility),
                    contentDescription = stringResource(
                        if (visible) Res.string.server_field_password_hide else Res.string.server_field_password_show,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.Spinner() {
    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
}

private val PREVIEW_INFO = ServerInfo(serverName = "Workshop", apiVersion = 1, setupDone = true, guestSignupEnabled = true)
private val PREVIEW_TARGET = ch.mathorck.easystock.viewmodels.server.ConnectTarget(ServerConnection("http://192.168.1.20:8080"))

@Preview(showBackground = true)
@Composable
private fun ConnectChoosePreview() {
    EasyStockTheme { ConnectContent(ConnectUiState(), ConnectActions(), onBack = {}, onOpenBackup = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ConnectFormPreview() {
    EasyStockTheme {
        ConnectContent(
            ConnectUiState(
                step = ConnectStep.Form(PREVIEW_TARGET, PREVIEW_INFO, FormMode.INVITE),
                invalidFields = setOf(FormField.PASSWORD),
            ),
            ConnectActions(),
            onBack = {},
            onOpenBackup = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectNotEmptyPreview() {
    EasyStockTheme {
        ConnectContent(ConnectUiState(step = ConnectStep.NotEmpty(PREVIEW_TARGET, PREVIEW_INFO)), ConnectActions(), onBack = {}, onOpenBackup = {})
    }
}
