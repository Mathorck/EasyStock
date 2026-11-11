package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.KtorAdminApi
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.rememberServerClient
import ch.mathorck.easystock.theme.EasyStockTheme
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_backup
import easystock.shared.generated.resources.admin_backup_summary
import easystock.shared.generated.resources.admin_invites
import easystock.shared.generated.resources.admin_invites_summary
import easystock.shared.generated.resources.admin_not_connected
import easystock.shared.generated.resources.admin_public_invite
import easystock.shared.generated.resources.admin_public_invite_summary
import easystock.shared.generated.resources.admin_settings
import easystock.shared.generated.resources.admin_settings_summary
import easystock.shared.generated.resources.admin_title
import easystock.shared.generated.resources.admin_users
import easystock.shared.generated.resources.admin_users_summary
import easystock.shared.generated.resources.ic_backup
import easystock.shared.generated.resources.ic_chevron_right
import easystock.shared.generated.resources.ic_group
import easystock.shared.generated.resources.ic_person_add
import easystock.shared.generated.resources.ic_print
import easystock.shared.generated.resources.ic_settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Screens of the administration. */
internal enum class AdminSection { Users, Invites, PublicInvite, Backup, Settings }

/**
 * Settings > Server > Administration. Opened only for a connected Admin (SettingsGates); the client is
 * built from the `sync_state` row, so without one nothing is sent.
 */
@Composable
fun AdministrationScreen(onBack: () -> Unit) {
    val db = rememberAppDatabase()
    val syncFlow = remember(db) { db.sessionDao().observeSyncState() }
    val syncState by syncFlow.collectAsStateWithLifecycle(null)
    val client = rememberServerClient(syncState)
    if (client == null) {
        NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
        SettingsSubpage(title = stringResource(Res.string.admin_title), onBack = onBack) {
            EmptyListHint(stringResource(Res.string.admin_not_connected))
        }
        return
    }
    val api = remember(client) { KtorAdminApi(client) }
    AdministrationContent(api, client.connection, onBack)
}

@Composable
private fun AdministrationContent(api: AdminApi, connection: ServerConnection, onBack: () -> Unit) {
    var section by rememberSaveable { mutableStateOf<AdminSection?>(null) }
    val close = { section = null }
    when (section) {
        AdminSection.Users -> AdminUsersScreen(api, connection, onBack = close)
        AdminSection.Invites -> AdminInvitesScreen(api, connection, onBack = close)
        AdminSection.PublicInvite -> PublicInviteScreen(api, connection, onBack = close)
        AdminSection.Backup -> ServerBackupScreen(api, onBack = close)
        AdminSection.Settings -> AdminSettingsScreen(api, onBack = close)
        null -> {
            NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
            AdministrationHub(onOpen = { section = it }, onBack = onBack)
        }
    }
}

@Composable
internal fun AdministrationHub(onOpen: (AdminSection) -> Unit, onBack: () -> Unit) {
    SettingsSubpage(title = stringResource(Res.string.admin_title), onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HubItem(Res.string.admin_users, Res.string.admin_users_summary, Res.drawable.ic_group) { onOpen(AdminSection.Users) }
            HubItem(Res.string.admin_invites, Res.string.admin_invites_summary, Res.drawable.ic_person_add) { onOpen(AdminSection.Invites) }
            HubItem(Res.string.admin_public_invite, Res.string.admin_public_invite_summary, Res.drawable.ic_print) {
                onOpen(AdminSection.PublicInvite)
            }
            HubItem(Res.string.admin_backup, Res.string.admin_backup_summary, Res.drawable.ic_backup) { onOpen(AdminSection.Backup) }
            HubItem(Res.string.admin_settings, Res.string.admin_settings_summary, Res.drawable.ic_settings) { onOpen(AdminSection.Settings) }
        }
    }
}

@Composable
private fun HubItem(
    title: org.jetbrains.compose.resources.StringResource,
    summary: org.jetbrains.compose.resources.StringResource,
    icon: DrawableResource,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun AdministrationHubPreview() {
    EasyStockTheme { AdministrationHub(onOpen = {}, onBack = {}) }
}
