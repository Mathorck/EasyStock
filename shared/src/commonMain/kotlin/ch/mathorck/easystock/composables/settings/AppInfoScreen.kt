package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.appinfo.AppInfo
import ch.mathorck.easystock.appinfo.ISSUES_URL
import ch.mathorck.easystock.appinfo.LICENSE_URL
import ch.mathorck.easystock.appinfo.REPO_URL
import ch.mathorck.easystock.appinfo.formatBytes
import ch.mathorck.easystock.appinfo.rememberAppInfoPlatform
import ch.mathorck.easystock.appinfo.toReportText
import ch.mathorck.easystock.backup.BACKUP_FORMAT_VERSION
import ch.mathorck.easystock.currentAppLanguage
import ch.mathorck.easystock.data.db.DB_VERSION
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.enums.AppLanguage
import ch.mathorck.easystock.nfc.NfcAvailability
import ch.mathorck.easystock.appinfo.DataCounts
import ch.mathorck.easystock.viewmodels.formatDateTime
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.app_info_app_language
import easystock.shared.generated.resources.app_info_application_id
import easystock.shared.generated.resources.app_info_articles
import easystock.shared.generated.resources.app_info_backup_format
import easystock.shared.generated.resources.app_info_build_debug
import easystock.shared.generated.resources.app_info_build_release
import easystock.shared.generated.resources.app_info_build_type
import easystock.shared.generated.resources.app_info_copied
import easystock.shared.generated.resources.app_info_copy
import easystock.shared.generated.resources.app_info_counts_note
import easystock.shared.generated.resources.app_info_db_size
import easystock.shared.generated.resources.app_info_db_version
import easystock.shared.generated.resources.app_info_device
import easystock.shared.generated.resources.app_info_free_storage
import easystock.shared.generated.resources.app_info_installed
import easystock.shared.generated.resources.app_info_issues
import easystock.shared.generated.resources.app_info_items
import easystock.shared.generated.resources.app_info_license
import easystock.shared.generated.resources.app_info_loading
import easystock.shared.generated.resources.app_info_locations
import easystock.shared.generated.resources.app_info_movements
import easystock.shared.generated.resources.app_info_nfc
import easystock.shared.generated.resources.app_info_nfc_absent
import easystock.shared.generated.resources.app_info_nfc_disabled
import easystock.shared.generated.resources.app_info_nfc_enabled
import easystock.shared.generated.resources.app_info_open_reviews
import easystock.shared.generated.resources.app_info_os
import easystock.shared.generated.resources.app_info_photos
import easystock.shared.generated.resources.app_info_photos_value
import easystock.shared.generated.resources.app_info_privacy
import easystock.shared.generated.resources.app_info_repository
import easystock.shared.generated.resources.app_info_section_app
import easystock.shared.generated.resources.app_info_section_data
import easystock.shared.generated.resources.app_info_section_device
import easystock.shared.generated.resources.app_info_section_support
import easystock.shared.generated.resources.app_info_system_locale
import easystock.shared.generated.resources.app_info_tags
import easystock.shared.generated.resources.app_info_updated
import easystock.shared.generated.resources.app_info_version
import easystock.shared.generated.resources.app_info_version_value
import easystock.shared.generated.resources.ic_content_copy
import easystock.shared.generated.resources.ic_description
import easystock.shared.generated.resources.ic_link
import easystock.shared.generated.resources.ic_open_in_new
import easystock.shared.generated.resources.settings_app_info
import easystock.shared.generated.resources.settings_language_english
import easystock.shared.generated.resources.settings_language_french
import easystock.shared.generated.resources.settings_language_german
import easystock.shared.generated.resources.settings_language_system
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings > About and app info. Local only: reads the package, the device and row counts, sends nothing.
 * Shows counts and technical values, never article names, serial numbers or tag IDs.
 */
@Composable
fun AppInfoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val platform = rememberAppInfoPlatform()
    var info by remember { mutableStateOf<AppInfo?>(null) }
    var copied by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val systemName = stringResource(Res.string.settings_language_system)
    val languageName = currentAppLanguage().let { language ->
        when (language) {
            AppLanguage.System -> systemName
            AppLanguage.English -> stringResource(Res.string.settings_language_english)
            AppLanguage.French -> stringResource(Res.string.settings_language_french)
            AppLanguage.German -> stringResource(Res.string.settings_language_german)
        }
    }
    // The copied text is English: the developer reads it, so it uses the tag, not the translated name
    val languageTag = currentAppLanguage().tag ?: "system"

    LaunchedEffect(Unit) {
        val dao = db.appInfoDao()
        val counts = DataCounts(
            articles = dao.articles(),
            locations = dao.locations(),
            items = dao.items(),
            stockMovements = dao.stockMovements(),
            nfcTags = dao.nfcTags(),
            openReviews = dao.openReviews(),
        )
        val platformInfo = platform.read()
        val zone = TimeZone.currentSystemDefault()
        info = AppInfo(
            platform = platformInfo,
            counts = counts,
            dbVersion = DB_VERSION,
            backupFormatVersion = BACKUP_FORMAT_VERSION,
            appLanguage = languageTag,
            installedAt = platformInfo.installedAt?.let { formatDateTime(it, zone) },
            updatedAt = platformInfo.updatedAt?.let { formatDateTime(it, zone) },
        )
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    SettingsSubpage(title = stringResource(Res.string.settings_app_info), onBack = onBack, modifier = modifier) {
        val loaded = info
        if (loaded == null) {
            EmptyListHint(stringResource(Res.string.app_info_loading))
            return@SettingsSubpage
        }
        val p = loaded.platform
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            InfoHeader(stringResource(Res.string.app_info_section_app))
            InfoRow(
                stringResource(Res.string.app_info_version),
                stringResource(Res.string.app_info_version_value, p.versionName, p.versionCode.toString()),
            )
            InfoRow(
                stringResource(Res.string.app_info_build_type),
                stringResource(if (p.debugBuild) Res.string.app_info_build_debug else Res.string.app_info_build_release),
            )
            InfoRow(stringResource(Res.string.app_info_application_id), p.applicationId)
            loaded.installedAt?.let { InfoRow(stringResource(Res.string.app_info_installed), it) }
            loaded.updatedAt?.let { InfoRow(stringResource(Res.string.app_info_updated), it) }

            InfoHeader(stringResource(Res.string.app_info_section_data))
            InfoRow(stringResource(Res.string.app_info_db_version), loaded.dbVersion.toString())
            InfoRow(stringResource(Res.string.app_info_backup_format), loaded.backupFormatVersion.toString())
            InfoRow(stringResource(Res.string.app_info_articles), loaded.counts.articles.toString())
            InfoRow(stringResource(Res.string.app_info_locations), loaded.counts.locations.toString())
            InfoRow(stringResource(Res.string.app_info_items), loaded.counts.items.toString())
            InfoRow(stringResource(Res.string.app_info_movements), loaded.counts.stockMovements.toString())
            InfoRow(stringResource(Res.string.app_info_tags), loaded.counts.nfcTags.toString())
            InfoRow(stringResource(Res.string.app_info_open_reviews), loaded.counts.openReviews.toString())
            Note(stringResource(Res.string.app_info_counts_note))
            InfoRow(stringResource(Res.string.app_info_db_size), formatBytes(p.databaseBytes))
            InfoRow(
                stringResource(Res.string.app_info_photos),
                stringResource(Res.string.app_info_photos_value, p.photoCount.toString(), formatBytes(p.photoBytes)),
            )

            InfoHeader(stringResource(Res.string.app_info_section_device))
            InfoRow(stringResource(Res.string.app_info_os), p.os)
            InfoRow(stringResource(Res.string.app_info_device), p.device)
            InfoRow(stringResource(Res.string.app_info_app_language), languageName)
            InfoRow(stringResource(Res.string.app_info_system_locale), p.locale)
            InfoRow(
                stringResource(Res.string.app_info_nfc),
                stringResource(
                    when (p.nfc) {
                        NfcAvailability.Enabled -> Res.string.app_info_nfc_enabled
                        NfcAvailability.Disabled -> Res.string.app_info_nfc_disabled
                        NfcAvailability.Unavailable, NfcAvailability.Unknown -> Res.string.app_info_nfc_absent
                    },
                ),
            )
            p.freeBytes?.let { InfoRow(stringResource(Res.string.app_info_free_storage), formatBytes(it)) }

            InfoHeader(stringResource(Res.string.app_info_section_support))
            ActionRow(
                title = stringResource(Res.string.app_info_copy),
                icon = Res.drawable.ic_content_copy,
                onClick = {
                    platform.copy(loaded.toReportText())
                    copied = true
                },
            )
            Note(if (copied) stringResource(Res.string.app_info_copied) else stringResource(Res.string.app_info_privacy))
            ActionRow(stringResource(Res.string.app_info_repository), Res.drawable.ic_link) { uriHandler.openUri(REPO_URL) }
            ActionRow(stringResource(Res.string.app_info_issues), Res.drawable.ic_open_in_new) { uriHandler.openUri(ISSUES_URL) }
            ActionRow(stringResource(Res.string.app_info_license), Res.drawable.ic_description) { uriHandler.openUri(LICENSE_URL) }
        }
    }
}

@Composable
private fun InfoHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ActionRow(title: String, icon: DrawableResource, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
