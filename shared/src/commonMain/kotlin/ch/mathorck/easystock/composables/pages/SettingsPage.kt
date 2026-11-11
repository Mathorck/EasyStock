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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import ch.mathorck.easystock.backup.WifiState
import ch.mathorck.easystock.backup.rememberBackupService
import ch.mathorck.easystock.composables.settings.AppInfoScreen
import ch.mathorck.easystock.viewmodels.BackupStep
import ch.mathorck.easystock.viewmodels.BackupViewModel
import kotlinx.coroutines.delay
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import ch.mathorck.easystock.currentAppLanguage
import ch.mathorck.easystock.composables.settings.AttributesScreen
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
enum class SettingsSubpageId { Attributes, Categories, Locations, Tags, Reviews, Backup, AppInfo }

@Composable
fun SettingsPage() {
    val service = rememberBackupService()
    var language by remember { mutableStateOf(currentAppLanguage()) }
    var subpage by rememberSaveable { mutableStateOf<SettingsSubpageId?>(null) }
    // Same view model as the backup screen: a Wi-Fi share goes on when that screen is closed
    val backup = viewModel(key = "settings-backup") { BackupViewModel(service) }
    val backupState by backup.state.collectAsStateWithLifecycle()
    val share = (backupState.step as? BackupStep.SendingWifi)?.takeIf { it.state.isActive() }?.deadline
    // Each subpage composes its own back handler, only while it is open
    when (subpage) {
        SettingsSubpageId.Attributes -> AttributesScreen(onBack = { subpage = null })
        SettingsSubpageId.Categories -> CategoriesScreen(onBack = { subpage = null })
        SettingsSubpageId.Locations -> LocationsScreen(onBack = { subpage = null })
        SettingsSubpageId.Tags -> TagsScreen(onBack = { subpage = null })
        SettingsSubpageId.Reviews -> ReviewsScreen(onBack = { subpage = null })
        SettingsSubpageId.Backup -> BackupScreen(onBack = { subpage = null })
        SettingsSubpageId.AppInfo -> AppInfoScreen(onBack = { subpage = null })
        null -> SettingsContent(
            language = language,
            onLanguageSelected = {
                language = it
                applyAppLanguage(it)
            },
            onOpen = { subpage = it },
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
    onOpen: (SettingsSubpageId) -> Unit = {},
    /** Set while a Wi-Fi backup share runs: shows a row to get back to it. */
    wifiShareDeadline: ComparableTimeMark? = null,
    onStopWifiShare: () -> Unit = {},
) {
    // Not saveable: the activity recreates on a language change, the dialog must not reopen
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (wifiShareDeadline != null) {
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

        SectionHeader(stringResource(Res.string.settings_section_catalogue))
        SubpageItem(
            title = stringResource(Res.string.settings_attributes),
            summary = stringResource(Res.string.settings_attributes_summary),
            icon = Res.drawable.ic_label,
            onClick = { onOpen(SettingsSubpageId.Attributes) },
        )
        SubpageItem(
            title = stringResource(Res.string.settings_categories),
            summary = stringResource(Res.string.settings_categories_summary),
            icon = Res.drawable.ic_category,
            onClick = { onOpen(SettingsSubpageId.Categories) },
        )
        SubpageItem(
            title = stringResource(Res.string.settings_locations),
            summary = stringResource(Res.string.settings_locations_summary),
            icon = Res.drawable.ic_location,
            onClick = { onOpen(SettingsSubpageId.Locations) },
        )
        SubpageItem(
            title = stringResource(Res.string.settings_tags),
            summary = stringResource(Res.string.settings_tags_summary),
            icon = Res.drawable.ic_nfc,
            onClick = { onOpen(SettingsSubpageId.Tags) },
        )

        SectionHeader(stringResource(Res.string.settings_section_data))
        SubpageItem(
            title = stringResource(Res.string.settings_reviews),
            summary = stringResource(Res.string.settings_reviews_summary),
            icon = Res.drawable.ic_fact_check,
            onClick = { onOpen(SettingsSubpageId.Reviews) },
        )
        SubpageItem(
            title = stringResource(Res.string.settings_backup),
            summary = stringResource(Res.string.settings_backup_summary),
            icon = Res.drawable.ic_backup,
            onClick = { onOpen(SettingsSubpageId.Backup) },
        )

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

private fun WifiState.isActive() = this is WifiState.Waiting || this is WifiState.Sending
