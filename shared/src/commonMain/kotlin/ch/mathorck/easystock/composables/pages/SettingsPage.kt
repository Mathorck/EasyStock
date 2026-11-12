package ch.mathorck.easystock.composables.pages

import ch.mathorck.easystock.theme.EasyStockTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mathorck.easystock.applyAppLanguage
import ch.mathorck.easystock.auth.AppPermissions
import ch.mathorck.easystock.auth.LocalPermissions
import ch.mathorck.easystock.auth.SettingsGates
import ch.mathorck.easystock.prefs.LocalAppPreferences
import ch.mathorck.easystock.theme.isDynamicColorSupported
import androidx.compose.runtime.collectAsState
import ch.mathorck.easystock.backup.WifiState
import ch.mathorck.easystock.backup.rememberBackupService
import ch.mathorck.easystock.composables.settings.AppInfoScreen
import ch.mathorck.easystock.composables.server.AccountScreen
import ch.mathorck.easystock.composables.server.ConnectScreen
import ch.mathorck.easystock.composables.server.admin.AdministrationScreen
import ch.mathorck.easystock.auth.rememberJoinPlatform
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.reset.rememberDataReset
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.viewmodels.server.ConnectStep
import ch.mathorck.easystock.viewmodels.server.ConnectViewModel
import kotlinx.coroutines.flow.map
import easystock.shared.generated.resources.ic_settings
import easystock.shared.generated.resources.settings_server
import easystock.shared.generated.resources.settings_server_not_connected
import easystock.shared.generated.resources.admin_title
import easystock.shared.generated.resources.ic_dns
import easystock.shared.generated.resources.settings_administration_summary
import easystock.shared.generated.resources.settings_section_server
import ch.mathorck.easystock.viewmodels.BackupStep
import ch.mathorck.easystock.viewmodels.BackupViewModel
import kotlinx.coroutines.delay
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import ch.mathorck.easystock.currentAppLanguage
import ch.mathorck.easystock.composables.attributes.AttributesScreen
import ch.mathorck.easystock.composables.settings.BackupScreen
import ch.mathorck.easystock.composables.settings.CategoriesScreen
import ch.mathorck.easystock.composables.settings.LocationsScreen
import ch.mathorck.easystock.composables.settings.TagsScreen
import ch.mathorck.easystock.composables.review.ReviewsScreen
import ch.mathorck.easystock.enums.AppLanguage
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.backup_wifi_active
import easystock.shared.generated.resources.backup_wifi_stop
import easystock.shared.generated.resources.ic_wifi
import easystock.shared.generated.resources.ic_backup
import easystock.shared.generated.resources.ic_category
import easystock.shared.generated.resources.ic_fact_check
import easystock.shared.generated.resources.settings_reviews
import easystock.shared.generated.resources.settings_reviews_summary
import easystock.shared.generated.resources.ic_chevron_right
import easystock.shared.generated.resources.ic_label
import easystock.shared.generated.resources.ic_location
import easystock.shared.generated.resources.ic_nfc
import easystock.shared.generated.resources.settings_tags
import easystock.shared.generated.resources.settings_tags_summary
import easystock.shared.generated.resources.settings_locations
import easystock.shared.generated.resources.settings_locations_summary
import easystock.shared.generated.resources.ic_info
import easystock.shared.generated.resources.ic_language
import easystock.shared.generated.resources.settings_app_info
import easystock.shared.generated.resources.settings_app_info_summary
import easystock.shared.generated.resources.settings_attributes
import easystock.shared.generated.resources.settings_backup
import easystock.shared.generated.resources.settings_backup_summary
import easystock.shared.generated.resources.settings_section_data
import easystock.shared.generated.resources.settings_attributes_summary
import easystock.shared.generated.resources.settings_categories
import easystock.shared.generated.resources.settings_categories_summary
import easystock.shared.generated.resources.settings_section_catalogue
import easystock.shared.generated.resources.settings_cancel
import easystock.shared.generated.resources.settings_language
import easystock.shared.generated.resources.settings_dynamic_color
import easystock.shared.generated.resources.settings_dynamic_color_summary
import easystock.shared.generated.resources.ic_palette
import easystock.shared.generated.resources.settings_language_english
import easystock.shared.generated.resources.settings_language_french
import easystock.shared.generated.resources.settings_language_german
import easystock.shared.generated.resources.settings_language_system
import easystock.shared.generated.resources.settings_section_general
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Full-screen subpages opened from the settings list. */
enum class SettingsSubpageId { Attributes, Categories, Locations, Tags, Reviews, Backup, AppInfo, Administration, Server }

/** The `sync_state` row once read: null while the database has not answered yet. */
private class SyncLoad(val state: SyncState?)

@Composable
fun SettingsPage(
    onDataReset: () -> Unit = {},
    /** After a logout: the phone is standalone and empty again. */
    onLoggedOut: () -> Unit = {},
    /** After a join, with the server address. */
    onJoined: (String) -> Unit = {},
) {
    val service = rememberBackupService()
    var language by remember { mutableStateOf(currentAppLanguage()) }
    val preferences = LocalAppPreferences.current
    val dynamicColor by preferences.dynamicColor.collectAsState()
    var subpage by rememberSaveable { mutableStateOf<SettingsSubpageId?>(null) }
    // Same view model as the backup screen: a Wi-Fi share goes on when that screen is closed
    val backup = viewModel(key = "settings-backup") { BackupViewModel(service) }
    val backupState by backup.state.collectAsStateWithLifecycle()
    val share = (backupState.step as? BackupStep.SendingWifi)?.takeIf { it.state.isActive() }?.deadline
    val gates = SettingsGates.of(LocalPermissions.current)
    val db = rememberAppDatabase()
    val syncFlow = remember(db) { db.sessionDao().observeSyncState().map(::SyncLoad) }
    val syncLoad by syncFlow.collectAsStateWithLifecycle(null)
    val syncState = syncLoad?.state
    // Built without any network call: the connect screen calls only after a scan or a typed address
    val joinPlatform = rememberJoinPlatform()
    val dataReset = rememberDataReset()
    val connect = viewModel(key = "settings-connect") {
        ConnectViewModel(
            joinApi = joinPlatform.joinApi,
            isPhoneEmpty = joinPlatform.emptyCheck::isEmpty,
            erase = {
                // Stops a Wi-Fi share and drops the temporary backup files of the erased data
                backup.reset()
                dataReset.run()
            },
            commit = joinPlatform.join::commit,
            deviceName = joinPlatform.deviceName,
            appVersion = joinPlatform.appVersion,
        )
    }
    // Joined: sync_state is written, the account screen shows; the connect screen starts afresh next time
    // (the sync_state row and the Done step may come in either order)
    val connectState by connect.state.collectAsStateWithLifecycle()
    val joinedUrl = syncState?.serverUrl?.takeIf { connectState.step == ConnectStep.Done }
    LaunchedEffect(joinedUrl) {
        if (joinedUrl != null) {
            connect.cancel()
            onJoined(joinedUrl.substringAfter("://"))
        }
    }
    // Where a subpage opened from Server goes back to (the backup before the erase, the administration)
    var returnTo by rememberSaveable { mutableStateOf<SettingsSubpageId?>(null) }
    val close = {
        subpage = returnTo
        returnTo = null
    }
    // Each subpage composes its own back handler, only while it is open. A subpage the role
    // can no longer open (role changed) shows the list instead.
    when (subpage?.takeIf { it in gates.subpages }) {
        SettingsSubpageId.Attributes -> AttributesScreen(onBack = { subpage = null })
        SettingsSubpageId.Categories -> CategoriesScreen(onBack = { subpage = null })
        SettingsSubpageId.Locations -> LocationsScreen(onBack = { subpage = null })
        SettingsSubpageId.Tags -> TagsScreen(onBack = { subpage = null })
        SettingsSubpageId.Reviews -> ReviewsScreen(onBack = { subpage = null })
        SettingsSubpageId.Backup -> BackupScreen(
            onBack = close,
            onJoinCode = { code ->
                // A server code scanned in the backup screen: the connect screen takes it from there
                if (syncLoad?.state == null) connect.offer(code)
                returnTo = null
                subpage = SettingsSubpageId.Server
            },
        )
        SettingsSubpageId.Administration -> AdministrationScreen(onBack = close)
        SettingsSubpageId.Server -> when {
            syncLoad == null -> Unit
            syncState == null -> ConnectScreen(
                vm = connect,
                onBack = { subpage = null },
                onOpenBackup = {
                    returnTo = SettingsSubpageId.Server
                    subpage = SettingsSubpageId.Backup
                },
            )
            else -> AccountScreen(
                syncState = syncState,
                onBack = { subpage = null },
                onOpenAdministration = {
                    returnTo = SettingsSubpageId.Server
                    subpage = SettingsSubpageId.Administration
                },
                onLoggedOut = {
                    backup.reset()
                    returnTo = null
                    subpage = null
                    onLoggedOut()
                },
            )
        }
        SettingsSubpageId.AppInfo -> AppInfoScreen(
            onBack = { subpage = null },
            onOpenBackup = { subpage = SettingsSubpageId.Backup },
            canReset = gates.resetData,
            onResetDone = {
                // Stops a Wi-Fi share and drops the temporary backup files of the erased data
                backup.reset()
                subpage = null
                onDataReset()
            },
        )
        null -> SettingsContent(
            language = language,
            onLanguageSelected = {
                language = it
                applyAppLanguage(it)
            },
            onOpen = {
                returnTo = null
                subpage = it
            },
            gates = gates,
            serverAddress = syncState?.let { ServerConnection.of(it).serverUrl.substringAfter("://") },
            dynamicColor = dynamicColor.takeIf { isDynamicColorSupported() },
            onDynamicColorChange = preferences::setDynamicColor,
            wifiShareDeadline = share,
            onStopWifiShare = backup::reset,
        )
    }
}

/** Stateless settings list, previewable without platform code. */
@Composable
fun SettingsContent(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    /** Phone colors on or off; null where the platform has none (the row is hidden). */
    dynamicColor: Boolean? = null,
    onDynamicColorChange: (Boolean) -> Unit = {},
    onOpen: (SettingsSubpageId) -> Unit = {},
    /** Entries the role can open, all of them in standalone mode. */
    gates: SettingsGates = SettingsGates.of(AppPermissions.Standalone),
    /** Set while a Wi-Fi backup share runs: shows a row to get back to it. */
    wifiShareDeadline: ComparableTimeMark? = null,
    onStopWifiShare: () -> Unit = {},
    /** The joined server, null in standalone mode. */
    serverAddress: String? = null,
) {
    // Not saveable: the activity recreates on a language change, the dialog must not reopen
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (wifiShareDeadline != null && SettingsSubpageId.Backup in gates.subpages) {
            WifiShareBanner(
                deadline = wifiShareDeadline,
                onOpen = { onOpen(SettingsSubpageId.Backup) },
                onStop = onStopWifiShare,
            )
        }
        SectionHeader(stringResource(Res.string.settings_section_general))
        ListItem(
            headlineContent = { Text(stringResource(Res.string.settings_language)) },
            supportingContent = { Text(stringResource(language.label())) },
            leadingContent = { Icon(painterResource(Res.drawable.ic_language), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable { showLanguageDialog = true },
        )
        if (dynamicColor != null) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(Res.string.settings_dynamic_color_summary)) },
                leadingContent = { Icon(painterResource(Res.drawable.ic_palette), contentDescription = null) },
                trailingContent = { Switch(checked = dynamicColor, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.toggleable(
                    value = dynamicColor,
                    role = Role.Switch,
                    onValueChange = onDynamicColorChange,
                ),
            )
        }

        if (gates.catalogue) SectionHeader(stringResource(Res.string.settings_section_catalogue))
        if (SettingsSubpageId.Attributes in gates.subpages) {
            SubpageItem(
                title = stringResource(Res.string.settings_attributes),
                summary = stringResource(Res.string.settings_attributes_summary),
                icon = Res.drawable.ic_label,
                onClick = { onOpen(SettingsSubpageId.Attributes) },
            )
        }
        if (SettingsSubpageId.Categories in gates.subpages) {
            SubpageItem(
                title = stringResource(Res.string.settings_categories),
                summary = stringResource(Res.string.settings_categories_summary),
                icon = Res.drawable.ic_category,
                onClick = { onOpen(SettingsSubpageId.Categories) },
            )
        }
        if (SettingsSubpageId.Locations in gates.subpages) {
            SubpageItem(
                title = stringResource(Res.string.settings_locations),
                summary = stringResource(Res.string.settings_locations_summary),
                icon = Res.drawable.ic_location,
                onClick = { onOpen(SettingsSubpageId.Locations) },
            )
        }
        if (SettingsSubpageId.Tags in gates.subpages) {
            SubpageItem(
                title = stringResource(Res.string.settings_tags),
                summary = stringResource(Res.string.settings_tags_summary),
                icon = Res.drawable.ic_nfc,
                onClick = { onOpen(SettingsSubpageId.Tags) },
            )
        }

        if (gates.data) SectionHeader(stringResource(Res.string.settings_section_data))
        if (SettingsSubpageId.Reviews in gates.subpages) {
            SubpageItem(
                title = stringResource(Res.string.settings_reviews),
                summary = stringResource(Res.string.settings_reviews_summary),
                icon = Res.drawable.ic_fact_check,
                onClick = { onOpen(SettingsSubpageId.Reviews) },
            )
        }
        if (SettingsSubpageId.Backup in gates.subpages) {
            SubpageItem(
                title = stringResource(Res.string.settings_backup),
                summary = stringResource(Res.string.settings_backup_summary),
                icon = Res.drawable.ic_backup,
                onClick = { onOpen(SettingsSubpageId.Backup) },
            )
        }

        SectionHeader(stringResource(Res.string.settings_section_server))
        SubpageItem(
            title = stringResource(Res.string.settings_server),
            summary = serverAddress ?: stringResource(Res.string.settings_server_not_connected),
            icon = Res.drawable.ic_dns,
            onClick = { onOpen(SettingsSubpageId.Server) },
        )
        // Only for a connected Admin
        if (gates.server) {
            SubpageItem(
                title = stringResource(Res.string.admin_title),
                summary = stringResource(Res.string.settings_administration_summary),
                icon = Res.drawable.ic_settings,
                onClick = { onOpen(SettingsSubpageId.Administration) },
            )
        }

        SubpageItem(
            title = stringResource(Res.string.settings_app_info),
            summary = stringResource(Res.string.settings_app_info_summary),
            icon = Res.drawable.ic_info,
            onClick = { onOpen(SettingsSubpageId.AppInfo) },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = language,
            onSelected = {
                showLanguageDialog = false
                if (it != language) onLanguageSelected(it)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun WifiShareBanner(deadline: ComparableTimeMark, onOpen: () -> Unit, onStop: () -> Unit) {
    var left by remember(deadline) { mutableStateOf(-deadline.elapsedNow()) }
    LaunchedEffect(deadline) {
        while (true) {
            left = -deadline.elapsedNow()
            delay(1.seconds)
        }
    }
    val seconds = left.inWholeSeconds.coerceAtLeast(0)
    val time = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    ListItem(
        headlineContent = { Text(stringResource(Res.string.backup_wifi_active, time)) },
        leadingContent = { Icon(painterResource(Res.drawable.ic_wifi), contentDescription = null) },
        trailingContent = { TextButton(onClick = onStop) { Text(stringResource(Res.string.backup_wifi_stop)) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

@Composable
private fun SubpageItem(title: String, summary: String, icon: DrawableResource, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun LanguageDialog(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(stringResource(option.label())) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onSelected(option) },
                        ),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) }
        },
    )
}

// Language names are written in their own language
private fun AppLanguage.label(): StringResource = when (this) {
    AppLanguage.System -> Res.string.settings_language_system
    AppLanguage.English -> Res.string.settings_language_english
    AppLanguage.French -> Res.string.settings_language_french
    AppLanguage.German -> Res.string.settings_language_german
}

@Preview(showBackground = true)
@Composable
private fun SettingsPagePreview() {
    EasyStockTheme {
        SettingsContent(language = AppLanguage.System, onLanguageSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPageDarkPreview() {
    EasyStockTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            SettingsContent(language = AppLanguage.French, onLanguageSelected = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPageViewerPreview() {
    EasyStockTheme {
        SettingsContent(
            language = AppLanguage.System,
            onLanguageSelected = {},
            gates = SettingsGates.of(AppPermissions.connectedAs(ch.mathorck.easystock.auth.Role.VIEWER)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPageGuestPreview() {
    EasyStockTheme {
        SettingsContent(
            language = AppLanguage.System,
            onLanguageSelected = {},
            gates = SettingsGates.of(AppPermissions.connectedAs(ch.mathorck.easystock.auth.Role.GUEST)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPageAdminPreview() {
    EasyStockTheme {
        SettingsContent(
            language = AppLanguage.System,
            onLanguageSelected = {},
            gates = SettingsGates.of(AppPermissions.connectedAs(ch.mathorck.easystock.auth.Role.ADMIN)),
        )
    }
}

private fun WifiState.isActive() = this is WifiState.Waiting || this is WifiState.Sending
