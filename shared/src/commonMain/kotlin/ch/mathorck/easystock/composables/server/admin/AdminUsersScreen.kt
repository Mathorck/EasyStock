package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.reset.matchesConfirmation
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.AdminDevice
import ch.mathorck.easystock.server.AdminUser
import ch.mathorck.easystock.server.JoinKind
import ch.mathorck.easystock.server.PendingPromotion
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.UserKind
import ch.mathorck.easystock.server.WireRole
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.admin.AdminUsersViewModel
import ch.mathorck.easystock.viewmodels.admin.Loaded
import ch.mathorck.easystock.viewmodels.admin.PromoteForm
import ch.mathorck.easystock.viewmodels.admin.UserMessage
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_cancel
import easystock.shared.generated.resources.admin_device_revoke
import easystock.shared.generated.resources.admin_device_revoke_message
import easystock.shared.generated.resources.admin_device_revoke_title
import easystock.shared.generated.resources.admin_device_revoked
import easystock.shared.generated.resources.admin_device_unnamed
import easystock.shared.generated.resources.admin_device_used
import easystock.shared.generated.resources.admin_kind_full
import easystock.shared.generated.resources.admin_kind_guest
import easystock.shared.generated.resources.admin_msg_deleted
import easystock.shared.generated.resources.admin_msg_device_revoked
import easystock.shared.generated.resources.admin_msg_disabled
import easystock.shared.generated.resources.admin_msg_enabled
import easystock.shared.generated.resources.admin_msg_promotion_cancelled
import easystock.shared.generated.resources.admin_msg_promotion_pending
import easystock.shared.generated.resources.admin_msg_role_changed
import easystock.shared.generated.resources.admin_promote
import easystock.shared.generated.resources.admin_promote_code_label
import easystock.shared.generated.resources.admin_promote_code_mismatch
import easystock.shared.generated.resources.admin_promote_code_ok
import easystock.shared.generated.resources.admin_promote_expected
import easystock.shared.generated.resources.admin_promote_message
import easystock.shared.generated.resources.admin_promote_role
import easystock.shared.generated.resources.admin_promote_title
import easystock.shared.generated.resources.admin_reset_qr_subtitle
import easystock.shared.generated.resources.admin_reset_qr_title
import easystock.shared.generated.resources.admin_user_cancel_promotion
import easystock.shared.generated.resources.admin_user_change_role
import easystock.shared.generated.resources.admin_user_delete
import easystock.shared.generated.resources.admin_user_delete_message
import easystock.shared.generated.resources.admin_user_delete_prompt
import easystock.shared.generated.resources.admin_user_delete_title
import easystock.shared.generated.resources.admin_user_devices
import easystock.shared.generated.resources.admin_user_disable
import easystock.shared.generated.resources.admin_user_disabled
import easystock.shared.generated.resources.admin_user_enable
import easystock.shared.generated.resources.admin_user_last_sync
import easystock.shared.generated.resources.admin_user_no_devices
import easystock.shared.generated.resources.admin_user_pending
import easystock.shared.generated.resources.admin_user_pending_detail
import easystock.shared.generated.resources.admin_user_reset_password
import easystock.shared.generated.resources.admin_user_short_code
import easystock.shared.generated.resources.admin_user_unknown_role
import easystock.shared.generated.resources.admin_user_unnamed
import easystock.shared.generated.resources.admin_users
import easystock.shared.generated.resources.admin_users_empty
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.ic_smartphone
import easystock.shared.generated.resources.ic_warning
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdminUsersScreen(api: AdminApi, connection: ServerConnection, onBack: () -> Unit) {
    val viewModel = viewModel(key = "admin-users-${connection.serverUrl}") { AdminUsersViewModel(api) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openUser by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.message, state.failure) {
        val text = state.failure?.let { getString(it.message()) } ?: state.message?.let { getString(it.text()) }
        if (text != null) {
            if (state.message == UserMessage.DELETED) openUser = null
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    val user = openUser?.let(state::user)
    Box(Modifier.fillMaxSize()) {
        if (user != null) {
            NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = { openUser = null })
            UserDetail(
                user = user,
                busy = state.busy,
                onBack = { openUser = null },
                onChangeRole = { viewModel.changeRole(user.id, it) },
                onSetDisabled = { viewModel.setDisabled(user.id, it) },
                onDelete = { viewModel.delete(user.id) },
                onResetPassword = { viewModel.resetPassword(user.id) },
                onRevokeDevice = viewModel::revokeDevice,
                onStartPromotion = { viewModel.startPromotion(user.id) },
                onCancelPromotion = { viewModel.cancelPromotion(user.id) },
            )
            state.promote?.let { form ->
                PromoteDialog(
                    form = form,
                    busy = state.busy,
                    onCodeChange = viewModel::typePromoteCode,
                    onRoleChange = viewModel::choosePromoteRole,
                    onConfirm = { viewModel.promote(user.id) },
                    onDismiss = viewModel::closePromotion,
                )
            }
            val reset = state.reset?.takeIf { state.resetFor == user.id }?.qrText(connection, JoinKind.RESET)
            if (reset != null) {
                FullScreenQr(
                    text = reset,
                    title = stringResource(Res.string.admin_reset_qr_title, user.displayName),
                    subtitle = stringResource(Res.string.admin_reset_qr_subtitle),
                    onClose = viewModel::closeReset,
                )
            }
        } else {
            NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
            UserList(state.users, onBack, viewModel::refresh, viewModel::retry, onOpen = { openUser = it })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun UserMessage.text() = when (this) {
    UserMessage.ROLE_CHANGED -> Res.string.admin_msg_role_changed
    UserMessage.DISABLED -> Res.string.admin_msg_disabled
    UserMessage.ENABLED -> Res.string.admin_msg_enabled
    UserMessage.DELETED -> Res.string.admin_msg_deleted
    UserMessage.PROMOTION_PENDING -> Res.string.admin_msg_promotion_pending
    UserMessage.PROMOTION_CANCELLED -> Res.string.admin_msg_promotion_cancelled
    UserMessage.DEVICE_REVOKED -> Res.string.admin_msg_device_revoked
}

@Composable
internal fun UserList(
    users: Loaded<List<AdminUser>>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    SettingsSubpage(title = stringResource(Res.string.admin_users), onBack = onBack) {
        LoadedContent(users, onRefresh, onRetry, isEmpty = { it.isEmpty() }, emptyText = stringResource(Res.string.admin_users_empty)) { list ->
            list.forEach { user -> UserRow(user, onClick = { onOpen(user.id) }) }
        }
    }
}

@Composable
private fun UserRow(user: AdminUser, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(user.displayName.ifBlank { stringResource(Res.string.admin_user_unnamed) }) },
        supportingContent = {
            Column {
                Text("${roleText(user)} · ${stringResource(if (user.isGuest) Res.string.admin_kind_guest else Res.string.admin_kind_full)}")
                Text(stringResource(Res.string.admin_user_last_sync, serverTime(user.lastSeenAt)))
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (user.pendingPromotion != null) Badge(stringResource(Res.string.admin_user_pending))
                if (user.disabled) Badge(stringResource(Res.string.admin_user_disabled), error = true)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Badge(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun roleText(user: AdminUser): String =
    user.appRole?.let { stringResource(it.label()) } ?: stringResource(Res.string.admin_user_unknown_role, user.role)

@Composable
internal fun UserDetail(
    user: AdminUser,
    busy: Boolean,
    onBack: () -> Unit,
    onChangeRole: (Role) -> Unit,
    onSetDisabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onResetPassword: () -> Unit,
    onRevokeDevice: (String) -> Unit,
    onStartPromotion: () -> Unit,
    onCancelPromotion: () -> Unit,
) {
    var chooseRole by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var revoke by rememberSaveable { mutableStateOf<String?>(null) }
    val name = user.displayName.ifBlank { stringResource(Res.string.admin_user_unnamed) }

    SettingsSubpage(title = name, onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(roleText(user)) },
                supportingContent = {
                    Column {
                        Text(stringResource(if (user.isGuest) Res.string.admin_kind_guest else Res.string.admin_kind_full))
                        user.userName?.let { Text(it) }
                        Text(stringResource(Res.string.admin_user_last_sync, serverTime(user.lastSeenAt)))
                        if (user.isGuest && user.shortCode != null) {
                            Text(stringResource(Res.string.admin_user_short_code, user.shortCode))
                        }
                        if (user.disabled) Text(stringResource(Res.string.admin_user_disabled), color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            user.pendingPromotion?.let { pending ->
                val role = WireRole.parse(pending.role)?.let { stringResource(it.label()) } ?: pending.role
                Text(
                    stringResource(Res.string.admin_user_pending_detail, role),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (user.isGuest) {
                    if (user.pendingPromotion == null) {
                        OutlinedButton(onClick = onStartPromotion, enabled = !busy && user.shortCode != null) {
                            Text(stringResource(Res.string.admin_promote))
                        }
                    } else {
                        OutlinedButton(onClick = onCancelPromotion, enabled = !busy) { Text(stringResource(Res.string.admin_user_cancel_promotion)) }
                    }
                } else {
                    OutlinedButton(onClick = { chooseRole = true }, enabled = !busy) { Text(stringResource(Res.string.admin_user_change_role)) }
                    OutlinedButton(onClick = onResetPassword, enabled = !busy) { Text(stringResource(Res.string.admin_user_reset_password)) }
                }
                OutlinedButton(onClick = { onSetDisabled(!user.disabled) }, enabled = !busy) {
                    Text(stringResource(if (user.disabled) Res.string.admin_user_enable else Res.string.admin_user_disable))
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.admin_user_delete)) }
            }
            HorizontalDivider()
            Text(
                stringResource(Res.string.admin_user_devices),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            if (user.devices.isEmpty()) EmptyListHint(stringResource(Res.string.admin_user_no_devices))
            user.devices.forEach { device -> DeviceRow(device, busy, onRevoke = { revoke = device.id }) }
        }
    }

    if (chooseRole) {
        RoleDialog(
            title = stringResource(Res.string.admin_user_change_role),
            roles = WireRole.assignable,
            selected = user.appRole,
            onSelected = {
                chooseRole = false
                if (it != user.appRole) onChangeRole(it)
            },
            onDismiss = { chooseRole = false },
        )
    }
    if (confirmDelete) {
        DeleteUserDialog(
            name = name,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    revoke?.let { id ->
        AlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text(stringResource(Res.string.admin_device_revoke_title)) },
            text = { Text(stringResource(Res.string.admin_device_revoke_message)) },
            confirmButton = {
                TextButton(onClick = {
                    revoke = null
                    onRevokeDevice(id)
                }) { Text(stringResource(Res.string.admin_device_revoke), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { revoke = null }) { Text(stringResource(Res.string.admin_cancel)) } },
        )
    }
}

@Composable
private fun DeviceRow(device: AdminDevice, busy: Boolean, onRevoke: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.deviceName.ifBlank { stringResource(Res.string.admin_device_unnamed) }) },
        supportingContent = {
            Text(
                if (device.revoked) stringResource(Res.string.admin_device_revoked)
                else stringResource(Res.string.admin_device_used, serverTime(device.lastUsedAt)),
            )
        },
        leadingContent = { Icon(painterResource(Res.drawable.ic_smartphone), contentDescription = null) },
        trailingContent = {
            if (!device.revoked) TextButton(onClick = onRevoke, enabled = !busy) { Text(stringResource(Res.string.admin_device_revoke)) }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
internal fun RoleDialog(title: String, roles: List<Role>, selected: Role?, onSelected: (Role) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { RoleChoice(roles, selected, onSelected) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_cancel)) } },
    )
}

@Composable
private fun RoleChoice(roles: List<Role>, selected: Role?, onSelected: (Role) -> Unit) {
    Column(Modifier.selectableGroup()) {
        roles.forEach { role ->
            ListItem(
                headlineContent = { Text(stringResource(role.label())) },
                leadingContent = { RadioButton(selected = role == selected, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = role == selected,
                    role = SemanticsRole.RadioButton,
                    onClick = { onSelected(role) },
                ),
            )
        }
    }
}

/** Deleting an account can't be undone: the Admin types its name. */
@Composable
private fun DeleteUserDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.admin_user_delete_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.admin_user_delete_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(Res.string.admin_user_delete_prompt, name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = matchesConfirmation(typed, name),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.admin_user_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_cancel)) } },
    )
}

/**
 * Promotion of a Guest: the Guest shows their short code on their phone, the Admin types it here.
 * "Promote" is on only when both match (case, spaces and dashes ignored).
 */
@Composable
internal fun PromoteDialog(
    form: PromoteForm,
    busy: Boolean,
    onCodeChange: (String) -> Unit,
    onRoleChange: (Role) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_promote_title)) },
        text = { PromoteFormContent(form, busy, onCodeChange, onRoleChange) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = form.canPromote && !busy) { Text(stringResource(Res.string.admin_promote)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_cancel)) } },
    )
}

@Composable
private fun PromoteFormContent(form: PromoteForm, busy: Boolean, onCodeChange: (String) -> Unit, onRoleChange: (Role) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.admin_promote_message), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.admin_promote_expected), style = MaterialTheme.typography.labelLarge)
        Text(form.expectedCode.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
        OutlinedTextField(
            value = form.typedCode,
            onValueChange = onCodeChange,
            label = { Text(stringResource(Res.string.admin_promote_code_label)) },
            singleLine = true,
            enabled = !busy,
            isError = form.typedCode.isNotBlank() && !form.codeMatches,
            supportingText = when {
                form.codeMatches -> {
                    { Text(stringResource(Res.string.admin_promote_code_ok)) }
                }
                form.typedCode.isNotBlank() -> {
                    { Text(stringResource(Res.string.admin_promote_code_mismatch)) }
                }
                else -> null
            },
            trailingIcon = if (form.codeMatches) {
                { Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.admin_promote_role), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PromoteForm.PROMOTION_ROLES.forEach { role ->
                AssistChip(
                    onClick = { onRoleChange(role) },
                    label = { Text(stringResource(role.label())) },
                    leadingIcon = if (role == form.role) {
                        { Icon(painterResource(Res.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                    enabled = !busy,
                )
            }
        }
    }
}

private val previewUsers = listOf(
    AdminUser("u1", "Ana", "ana", "admin", UserKind.FULL, lastSeenAt = "2026-11-10T08:00:00Z"),
    AdminUser("u2", "Guest 1", null, "guest", UserKind.GUEST, shortCode = "K7-4QX", pendingPromotion = PendingPromotion("member")),
    AdminUser("u3", "Marc", "marc", "viewer", UserKind.FULL, disabledAt = "2026-11-01T08:00:00Z"),
)

@Preview(showBackground = true)
@Composable
private fun UserListPreview() {
    EasyStockTheme { UserList(Loaded(previewUsers, loading = false), {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun UserDetailPreview() {
    EasyStockTheme {
        UserDetail(
            previewUsers[0].copy(devices = listOf(AdminDevice("d1", deviceName = "Pixel 10", lastUsedAt = "2026-11-10T08:00:00Z"))),
            busy = false, onBack = {}, onChangeRole = {}, onSetDisabled = {}, onDelete = {}, onResetPassword = {},
            onRevokeDevice = {}, onStartPromotion = {}, onCancelPromotion = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PromoteDialogPreview() {
    EasyStockTheme {
        PromoteDialog(PromoteForm("K7-4QX", typedCode = "k7 4qx"), busy = false, onCodeChange = {}, onRoleChange = {}, onConfirm = {}, onDismiss = {})
    }
}
