package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.Invite
import ch.mathorck.easystock.server.JoinKind
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.WireRole
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.admin.AdminInvitesViewModel
import ch.mathorck.easystock.viewmodels.admin.Loaded
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_cancel
import easystock.shared.generated.resources.admin_invite_cancel
import easystock.shared.generated.resources.admin_invite_cancel_message
import easystock.shared.generated.resources.admin_invite_cancel_title
import easystock.shared.generated.resources.admin_invite_create
import easystock.shared.generated.resources.admin_invite_expires
import easystock.shared.generated.resources.admin_invite_no_qr
import easystock.shared.generated.resources.admin_invite_qr_subtitle
import easystock.shared.generated.resources.admin_invite_qr_title
import easystock.shared.generated.resources.admin_invite_role
import easystock.shared.generated.resources.admin_invites
import easystock.shared.generated.resources.admin_invites_empty
import easystock.shared.generated.resources.admin_user_unknown_role
import easystock.shared.generated.resources.ic_add
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdminInvitesScreen(api: AdminApi, connection: ServerConnection, onBack: () -> Unit) {
    val viewModel = viewModel(key = "admin-invites-${connection.serverUrl}") { AdminInvitesViewModel(api) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var chooseRole by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    LaunchedEffect(state.failure) {
        state.failure?.let {
            snackbar.showSnackbar(getString(it.message()))
            viewModel.consumeFailure()
        }
    }

    Box(Modifier.fillMaxSize()) {
        InviteList(state.invites, state.busy, onBack, viewModel::refresh, viewModel::retry, onCancel = viewModel::cancel)
        ExtendedFloatingActionButton(
            onClick = { if (!state.busy) chooseRole = true },
            icon = { Icon(painterResource(Res.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(Res.string.admin_invite_create)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (chooseRole) {
        RoleDialog(
            title = stringResource(Res.string.admin_invite_role),
            roles = WireRole.assignable,
            selected = null,
            onSelected = {
                chooseRole = false
                viewModel.create(it)
            },
            onDismiss = { chooseRole = false },
        )
    }
    state.created?.let { invite ->
        val text = invite.grant.qrText(connection, JoinKind.INVITE)
        val role = WireRole.parse(invite.role)?.let { stringResource(it.label()) } ?: invite.role
        if (text != null) {
            FullScreenQr(
                text = text,
                title = stringResource(Res.string.admin_invite_qr_title, role),
                subtitle = stringResource(Res.string.admin_invite_qr_subtitle, serverTime(invite.expiresAt)),
                onClose = viewModel::closeCreated,
            )
        } else {
            AlertDialog(
                onDismissRequest = viewModel::closeCreated,
                text = { Text(stringResource(Res.string.admin_invite_no_qr)) },
                confirmButton = { TextButton(onClick = viewModel::closeCreated) { Text(stringResource(Res.string.admin_cancel)) } },
            )
        }
    }
}

@Composable
internal fun InviteList(
    invites: Loaded<List<Invite>>,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onCancel: (String) -> Unit,
) {
    var cancel by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsSubpage(title = stringResource(Res.string.admin_invites), onBack = onBack) {
        LoadedContent(invites, onRefresh, onRetry, isEmpty = { it.isEmpty() }, emptyText = stringResource(Res.string.admin_invites_empty)) { list ->
            list.forEach { invite ->
                ListItem(
                    headlineContent = {
                        Text(WireRole.parse(invite.role)?.let { stringResource(it.label()) } ?: stringResource(Res.string.admin_user_unknown_role, invite.role))
                    },
                    supportingContent = { Text(stringResource(Res.string.admin_invite_expires, serverTime(invite.expiresAt))) },
                    trailingContent = {
                        TextButton(onClick = { cancel = invite.id }, enabled = !busy) { Text(stringResource(Res.string.admin_invite_cancel)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        }
    }
    cancel?.let { id ->
        AlertDialog(
            onDismissRequest = { cancel = null },
            title = { Text(stringResource(Res.string.admin_invite_cancel_title)) },
            text = { Text(stringResource(Res.string.admin_invite_cancel_message)) },
            confirmButton = {
                TextButton(onClick = {
                    cancel = null
                    onCancel(id)
                }) { Text(stringResource(Res.string.admin_invite_cancel), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cancel = null }) { Text(stringResource(Res.string.admin_cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InviteListPreview() {
    EasyStockTheme {
        InviteList(
            Loaded(
                listOf(
                    Invite("i1", "member", expiresAt = "2026-11-18T08:00:00Z"),
                    Invite("i2", "viewer", expiresAt = "2026-11-12T08:00:00Z"),
                ),
                loading = false,
            ),
            busy = false, onBack = {}, onRefresh = {}, onRetry = {}, onCancel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InviteQrPreview() {
    EasyStockTheme {
        FullScreenQrContent("easystock://join?u=https%3A%2F%2F192.168.1.10&k=invite&t=abc", "Invite: Editor", "Valid until 18.11.2026", {})
    }
}
