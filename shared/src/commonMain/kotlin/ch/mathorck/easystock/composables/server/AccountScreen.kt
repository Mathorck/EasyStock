package ch.mathorck.easystock.composables.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.auth.rememberSessionStore
import ch.mathorck.easystock.auth.storedRoleOf
import ch.mathorck.easystock.composables.server.admin.label
import ch.mathorck.easystock.composables.server.admin.serverTime
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.reset.rememberDataReset
import ch.mathorck.easystock.server.KtorAccountApi
import ch.mathorck.easystock.server.MeAnswer
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.WireRole
import ch.mathorck.easystock.server.rememberServerClient
import ch.mathorck.easystock.sync.SyncError
import ch.mathorck.easystock.sync.SyncStatus
import ch.mathorck.easystock.sync.rememberSyncEngine
import androidx.compose.material3.Badge
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.server.AccountUiState
import ch.mathorck.easystock.viewmodels.server.AccountViewModel
import ch.mathorck.easystock.viewmodels.server.FormField
import ch.mathorck.easystock.viewmodels.server.LogoutStep
import ch.mathorck.easystock.viewmodels.server.PromotionForm
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_chevron_right
import easystock.shared.generated.resources.ic_settings
import easystock.shared.generated.resources.ic_warning
import easystock.shared.generated.resources.server_account_address
import easystock.shared.generated.resources.server_account_administration
import easystock.shared.generated.resources.server_account_last_sync
import easystock.shared.generated.resources.server_account_name
import easystock.shared.generated.resources.server_account_offline
import easystock.shared.generated.resources.server_account_role
import easystock.shared.generated.resources.server_account_short_code
import easystock.shared.generated.resources.server_account_short_code_hint
import easystock.shared.generated.resources.server_cancel
import easystock.shared.generated.resources.server_error_title
import easystock.shared.generated.resources.server_field_password
import easystock.shared.generated.resources.server_field_password_rule
import easystock.shared.generated.resources.server_field_username
import easystock.shared.generated.resources.server_field_username_rule
import easystock.shared.generated.resources.server_form_submit_create
import easystock.shared.generated.resources.server_logout
import easystock.shared.generated.resources.server_logout_confirm
import easystock.shared.generated.resources.server_logout_guest_message
import easystock.shared.generated.resources.server_logout_lose
import easystock.shared.generated.resources.server_logout_message
import easystock.shared.generated.resources.server_logout_offline_confirm
import easystock.shared.generated.resources.server_logout_offline_message
import easystock.shared.generated.resources.server_logout_offline_title
import easystock.shared.generated.resources.server_logout_pending
import easystock.shared.generated.resources.server_logout_running
import easystock.shared.generated.resources.server_logout_stay
import easystock.shared.generated.resources.server_logout_sync_first
import easystock.shared.generated.resources.logout_sync_failed
import easystock.shared.generated.resources.logout_syncing
import easystock.shared.generated.resources.ic_sync
import easystock.shared.generated.resources.rejected_summary
import easystock.shared.generated.resources.rejected_title
import easystock.shared.generated.resources.sync_error_app_too_old
import easystock.shared.generated.resources.sync_error_forbidden
import easystock.shared.generated.resources.sync_error_network
import easystock.shared.generated.resources.sync_error_server
import easystock.shared.generated.resources.sync_full_pull
import easystock.shared.generated.resources.sync_never
import easystock.shared.generated.resources.sync_now
import easystock.shared.generated.resources.sync_running
import easystock.shared.generated.resources.server_logout_title
import easystock.shared.generated.resources.server_ok
import easystock.shared.generated.resources.server_promotion_banner
import easystock.shared.generated.resources.server_promotion_open
import easystock.shared.generated.resources.server_promotion_title
import easystock.shared.generated.resources.settings_server
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The user's actions on the account screen, so that the content can be previewed without a view model. */
internal class AccountActions(
    val askLogout: () -> Unit = {},
    val cancelLogout: () -> Unit = {},
    val confirmLogout: () -> Unit = {},
    val syncThenLogout: () -> Unit = {},
    val syncNow: () -> Unit = {},
    val openRejected: () -> Unit = {},
    val logoutOffline: () -> Unit = {},
    val stayConnected: () -> Unit = {},
    val openPromotion: () -> Unit = {},
    val closePromotion: () -> Unit = {},
    val promotionUsernameChanged: (String) -> Unit = {},
    val promotionPasswordChanged: (String) -> Unit = {},
    val submitPromotion: () -> Unit = {},
    val dismissError: () -> Unit = {},
    val openAdministration: () -> Unit = {},
)

/**
 * Settings › Server on a connected phone: the own account, a pending promotion, the logout. [onLoggedOut]
 * runs after the wipe, when the phone is standalone and empty again.
 */
@Composable
fun AccountScreen(
    syncState: SyncState,
    onBack: () -> Unit,
    onOpenAdministration: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val db = rememberAppDatabase()
    val dao = remember(db) { db.sessionDao() }
    val session = rememberSessionStore()
    val dataReset = rememberDataReset()
    val client = rememberServerClient(syncState) ?: return
    val engine = rememberSyncEngine()
    var showRejected by rememberSaveable { mutableStateOf(false) }
    val loggedOut by rememberUpdatedState(onLoggedOut)
    // One view model per client: a new connection (after a logout) gets a fresh one
    val vm = viewModel(key = "server-account-${client.hashCode()}") {
        AccountViewModel(
            api = KtorAccountApi(client),
            pendingChanges = dao::outboxCount,
            syncFirst = {
                engine?.sync()
                dao.outboxCount()
            },
            onMe = { me ->
                if (me.role.isNotEmpty()) dao.updateRole(storedRoleOf(me.role))
                if (me.id.isNotEmpty()) dao.upsertServerUser(ServerUser(me.id, me.displayName))
            },
            onPromoted = { answer ->
                // The client reads the tokens from the session at each call
                session.saveRefreshToken(answer.refreshToken)
                session.accessToken = answer.accessToken
                answer.user?.let { user ->
                    dao.promoted(storedRoleOf(user.role))
                    dao.upsertServerUser(ServerUser(user.id, user.displayName))
                }
                // promoted() set since to 0: the full pull of what the new role can see
                engine?.requestSync()
            },
            wipe = {
                dataReset.run()
                loggedOut()
            },
        )
    }
    LaunchedEffect(vm) { vm.refresh() }
    val state by vm.state.collectAsStateWithLifecycle()
    val statusFlow = remember(engine) { engine?.status ?: MutableStateFlow(SyncStatus()) }
    val syncStatus by statusFlow.collectAsStateWithLifecycle()
    val syncDao = remember(db) { db.syncDao() }
    val rejectedFlow = remember(syncDao) { syncDao.observeRejectedChanges() }
    val rejected by rejectedFlow.collectAsStateWithLifecycle(emptyList())
    val nameFlow = remember(dao, syncState.userId) { dao.observeDisplayName(syncState.userId) }
    val storedName by nameFlow.collectAsStateWithLifecycle(null)
    val role = Role.fromDb(syncState.role)
    if (showRejected) {
        RejectedChangesScreen(onBack = { showRejected = false })
        return
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
    AccountContent(
        connection = ServerConnection.of(syncState),
        role = role,
        storedName = storedName,
        lastSyncAt = syncState.lastSyncAt,
        syncStatus = syncStatus,
        rejected = rejected.size,
        unseenRejected = rejected.count { it.seenAt == null },
        state = state,
        onBack = onBack,
        actions = AccountActions(
            askLogout = { vm.askLogout(guest = role == Role.GUEST) },
            cancelLogout = vm::cancelLogout,
            confirmLogout = vm::confirmLogout,
            syncThenLogout = vm::syncThenLogout,
            syncNow = { engine?.requestSync() },
            openRejected = { showRejected = true },
            logoutOffline = vm::logoutOffline,
            stayConnected = vm::stayConnected,
            openPromotion = vm::openPromotion,
            closePromotion = vm::closePromotion,
            promotionUsernameChanged = vm::promotionUsernameChanged,
            promotionPasswordChanged = vm::promotionPasswordChanged,
            submitPromotion = vm::submitPromotion,
            dismissError = vm::dismissError,
            openAdministration = onOpenAdministration,
        ),
    )
}

@Composable
internal fun AccountContent(
    connection: ServerConnection,
    role: Role?,
    storedName: String?,
    lastSyncAt: Long?,
    state: AccountUiState,
    onBack: () -> Unit,
    actions: AccountActions,
    syncStatus: SyncStatus = SyncStatus(),
    rejected: Int = 0,
    unseenRejected: Int = 0,
) {
    val me = state.me
    SettingsSubpage(title = stringResource(Res.string.settings_server), onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (state.meError != null) {
                Text(
                    stringResource(Res.string.server_account_offline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            me?.pendingPromotion?.let { promotion ->
                val promotedRole = WireRole.parse(promotion.role)?.label()?.let { stringResource(it) } ?: promotion.role
                PromotionBanner(promotedRole, actions.openPromotion)
            }
            InfoRow(stringResource(Res.string.server_account_address), connection.host()) {
                ConnectionSecurity(connection, Modifier.padding(top = 4.dp))
            }
            InfoRow(stringResource(Res.string.server_account_name), me?.displayName?.takeIf { it.isNotBlank() } ?: storedName ?: "")
            InfoRow(stringResource(Res.string.server_account_role), role?.let { stringResource(it.label()) } ?: "")
            val shortCode = me?.shortCode
            if (role == Role.GUEST && !shortCode.isNullOrBlank()) ShortCodeCard(shortCode)
            SyncRow(lastSyncAt, syncStatus, actions.syncNow)
            if (rejected > 0) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.rejected_title)) },
                    supportingContent = { Text(stringResource(Res.string.rejected_summary, rejected)) },
                    leadingContent = { Icon(painterResource(Res.drawable.ic_warning), contentDescription = null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (unseenRejected > 0) Badge { Text("$unseenRejected") }
                            Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable(onClick = actions.openRejected),
                )
            }
            if (role == Role.ADMIN) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.server_account_administration)) },
                    leadingContent = { Icon(painterResource(Res.drawable.ic_settings), contentDescription = null) },
                    trailingContent = { Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable(onClick = actions.openAdministration),
                )
            }
            OutlinedButton(
                onClick = actions.askLogout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(Res.string.server_logout)) }
        }
    }

    LogoutDialogs(state.logout, actions)
    state.promotion?.let { PromotionDialog(it, actions) }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = actions.dismissError,
            title = { Text(stringResource(Res.string.server_error_title)) },
            text = { Text(stringResource(error.message())) },
            confirmButton = { TextButton(onClick = actions.dismissError) { Text(stringResource(Res.string.server_ok)) } },
        )
    }
}

/** Last sync, the running sync (with the full pull progress) or why the last one failed, and "Sync now". */
@Composable
private fun SyncRow(lastSyncAt: Long?, status: SyncStatus, onSyncNow: () -> Unit) {
    ListItem(
        overlineContent = { Text(stringResource(Res.string.server_account_last_sync)) },
        headlineContent = {
            Text(
                lastSyncAt?.let { serverTime(Instant.fromEpochMilliseconds(it).toString()) }
                    ?: stringResource(Res.string.sync_never),
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val error = status.error
                when {
                    status.running -> {
                        val rows = status.fullPullRows
                        Text(if (rows != null) stringResource(Res.string.sync_full_pull, rows) else stringResource(Res.string.sync_running))
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    error != null && error != SyncError.SESSION_REVOKED -> Text(
                        stringResource(
                            when (error) {
                                SyncError.APP_TOO_OLD -> Res.string.sync_error_app_too_old
                                SyncError.FORBIDDEN -> Res.string.sync_error_forbidden
                                SyncError.NETWORK -> Res.string.sync_error_network
                                else -> Res.string.sync_error_server
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        trailingContent = {
            TextButton(onClick = onSyncNow, enabled = !status.running && !status.paused) {
                Icon(painterResource(Res.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.sync_now), modifier = Modifier.padding(start = 4.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun InfoRow(label: String, value: String, extra: @Composable () -> Unit = {}) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        supportingContent = { extra() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun PromotionBanner(role: String, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.server_promotion_banner, role), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpen, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                Text(stringResource(Res.string.server_promotion_open))
            }
        }
    }
}

/** The short code of a Guest, big: read aloud or shown to the Admin, who finds the Guest with it. */
@Composable
private fun ShortCodeCard(code: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.server_account_short_code), style = MaterialTheme.typography.titleSmall)
            Text(
                code,
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                stringResource(Res.string.server_account_short_code_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LogoutDialogs(step: LogoutStep?, actions: AccountActions) {
    when (step) {
        is LogoutStep.Confirm -> AlertDialog(
            onDismissRequest = actions.cancelLogout,
            title = { Text(stringResource(Res.string.server_logout_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (step.guest) Res.string.server_logout_guest_message else Res.string.server_logout_message))
                    if (step.pending > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                stringResource(Res.string.server_logout_pending, step.pending),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (step.syncFailed) {
                            Text(stringResource(Res.string.logout_sync_failed), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = actions.syncThenLogout, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.server_logout_sync_first))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = actions.confirmLogout,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(if (step.pending > 0) Res.string.server_logout_lose else Res.string.server_logout_confirm))
                }
            },
            dismissButton = { TextButton(onClick = actions.cancelLogout) { Text(stringResource(Res.string.server_cancel)) } },
        )
        is LogoutStep.Syncing, LogoutStep.Working, LogoutStep.Done -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(
                        stringResource(if (step is LogoutStep.Syncing) Res.string.logout_syncing else Res.string.server_logout_running),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            },
            confirmButton = {},
        )
        LogoutStep.Offline -> AlertDialog(
            onDismissRequest = actions.stayConnected,
            title = { Text(stringResource(Res.string.server_logout_offline_title)) },
            text = { Text(stringResource(Res.string.server_logout_offline_message)) },
            confirmButton = {
                TextButton(
                    onClick = actions.logoutOffline,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.server_logout_offline_confirm)) }
            },
            dismissButton = { TextButton(onClick = actions.stayConnected) { Text(stringResource(Res.string.server_logout_stay)) } },
        )
        null -> Unit
    }
}

@Composable
private fun PromotionDialog(form: PromotionForm, actions: AccountActions) {
    AlertDialog(
        onDismissRequest = actions.closePromotion,
        properties = DialogProperties(dismissOnBackPress = !form.busy, dismissOnClickOutside = false),
        title = { Text(stringResource(Res.string.server_promotion_title)) },
        text = {
            Column {
                FormTextField(
                    value = form.username,
                    onValueChange = actions.promotionUsernameChanged,
                    label = stringResource(Res.string.server_field_username),
                    rule = stringResource(Res.string.server_field_username_rule),
                    invalid = FormField.USERNAME in form.invalidFields,
                    enabled = !form.busy,
                )
                PasswordField(
                    value = form.password,
                    onValueChange = actions.promotionPasswordChanged,
                    label = stringResource(Res.string.server_field_password),
                    rule = stringResource(Res.string.server_field_password_rule),
                    invalid = FormField.PASSWORD in form.invalidFields,
                    enabled = !form.busy,
                    onDone = actions.submitPromotion,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.submitPromotion, enabled = !form.busy) {
                if (form.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.server_form_submit_create))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.closePromotion, enabled = !form.busy) { Text(stringResource(Res.string.server_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountGuestPreview() {
    EasyStockTheme {
        AccountContent(
            connection = ServerConnection("http://192.168.1.20:8080"),
            role = Role.GUEST,
            storedName = "Ann",
            lastSyncAt = null,
            state = AccountUiState(
                me = MeAnswer(
                    id = "u1",
                    displayName = "Ann",
                    role = "guest",
                    shortCode = "K7Q-42",
                    pendingPromotion = ch.mathorck.easystock.server.PendingPromotion("member"),
                ),
            ),
            onBack = {},
            actions = AccountActions(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountAdminPreview() {
    EasyStockTheme {
        AccountContent(
            connection = ServerConnection("https://stock.example.org", "ab".repeat(32)),
            role = Role.ADMIN,
            storedName = "Ann",
            lastSyncAt = null,
            state = AccountUiState(),
            onBack = {},
            actions = AccountActions(),
        )
    }
}
