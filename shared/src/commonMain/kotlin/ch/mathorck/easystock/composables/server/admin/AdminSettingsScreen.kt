package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.AdminSettings
import ch.mathorck.easystock.server.WireRole
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.admin.AdminSettingsState
import ch.mathorck.easystock.viewmodels.admin.AdminSettingsViewModel
import ch.mathorck.easystock.viewmodels.admin.Loaded
import ch.mathorck.easystock.viewmodels.admin.RETENTION_CHOICES
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_settings
import easystock.shared.generated.resources.admin_settings_alert_roles
import easystock.shared.generated.resources.admin_settings_alert_roles_summary
import easystock.shared.generated.resources.admin_settings_retention
import easystock.shared.generated.resources.admin_settings_retention_days
import easystock.shared.generated.resources.admin_settings_retention_forever
import easystock.shared.generated.resources.admin_settings_retention_summary
import easystock.shared.generated.resources.admin_settings_saved
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdminSettingsScreen(api: AdminApi, onBack: () -> Unit) {
    val viewModel = viewModel(key = "admin-settings") { AdminSettingsViewModel(api) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    LaunchedEffect(state.failure, state.saved) {
        val text = state.failure?.let { getString(it.message()) } ?: if (state.saved) getString(Res.string.admin_settings_saved) else null
        if (text != null) {
            viewModel.consumeMessage()
            snackbar.showSnackbar(text)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AdminSettingsContent(state, onBack, viewModel::refresh, viewModel::retry, viewModel::setRetention, viewModel::setAlertRole)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun AdminSettingsContent(
    state: AdminSettingsState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRetention: (Int?) -> Unit,
    onAlertRole: (Role, Boolean) -> Unit,
) {
    SettingsSubpage(title = stringResource(Res.string.admin_settings), onBack = onBack) {
        if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        LoadedContent(state.settings, onRefresh, onRetry, isEmpty = { false }, emptyText = "") { settings ->
            SectionTitle(stringResource(Res.string.admin_settings_retention))
            Hint(stringResource(Res.string.admin_settings_retention_summary))
            // A value the server has that is not in the list is shown too
            val choices = (RETENTION_CHOICES + settings.movementAuthorRetentionDays).distinct()
            choices.forEach { days ->
                val selected = days == settings.movementAuthorRetentionDays
                ListItem(
                    headlineContent = {
                        Text(
                            if (days == null) stringResource(Res.string.admin_settings_retention_forever)
                            else pluralStringResource(Res.plurals.admin_settings_retention_days, days, days),
                        )
                    },
                    leadingContent = { RadioButton(selected = selected, onClick = null, enabled = !state.saving) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.selectable(
                        selected = selected,
                        enabled = !state.saving,
                        role = SemanticsRole.RadioButton,
                        onClick = { onRetention(days) },
                    ),
                )
            }
            val alertRoles = state.alertRoles
            if (alertRoles != null) {
                SectionTitle(stringResource(Res.string.admin_settings_alert_roles))
                Hint(stringResource(Res.string.admin_settings_alert_roles_summary))
                WireRole.assignable.forEach { role ->
                    val on = role in alertRoles
                    ListItem(
                        headlineContent = { Text(stringResource(role.label())) },
                        leadingContent = { Checkbox(checked = on, onCheckedChange = null, enabled = !state.saving) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.toggleable(
                            value = on,
                            enabled = !state.saving,
                            role = SemanticsRole.Checkbox,
                            onValueChange = { onAlertRole(role, it) },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun AdminSettingsPreview() {
    EasyStockTheme {
        AdminSettingsContent(
            AdminSettingsState(Loaded(AdminSettings("Atelier", 90, listOf("admin", "member")), loading = false)),
            {}, {}, {}, {}, { _, _ -> },
        )
    }
}
