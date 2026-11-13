package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.AdminSettings
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.admin.AdminSettingsState
import ch.mathorck.easystock.viewmodels.admin.AdminSettingsViewModel
import ch.mathorck.easystock.viewmodels.admin.Loaded
import ch.mathorck.easystock.viewmodels.admin.canSaveServerName
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_settings
import easystock.shared.generated.resources.admin_settings_saved
import easystock.shared.generated.resources.admin_settings_summary
import easystock.shared.generated.resources.server_save
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdminSettingsScreen(api: AdminApi, onBack: () -> Unit) {
    val viewModel = viewModel(key = "admin-settings-${api.hashCode()}") { AdminSettingsViewModel(api) }
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
        AdminSettingsContent(state, onBack, viewModel::refresh, viewModel::retry, viewModel::setServerName)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun AdminSettingsContent(
    state: AdminSettingsState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSave: (String) -> Unit,
) {
    SettingsSubpage(title = stringResource(Res.string.admin_settings), onBack = onBack) {
        if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        LoadedContent(state.settings, onRefresh, onRetry, isEmpty = { false }, emptyText = "") { settings ->
            // The last loaded name: a new one replaces the field only when the user has not edited it
            var loadedName by remember { mutableStateOf(settings.serverName) }
            var name by remember { mutableStateOf(settings.serverName) }
            if (settings.serverName != loadedName) {
                if (name == loadedName || name.trim() == settings.serverName) name = settings.serverName
                loadedName = settings.serverName
            }
            val canSave = state.canSaveServerName(name)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.admin_settings_summary)) },
                singleLine = true,
                enabled = !state.saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(name) }),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Button(
                onClick = { onSave(name) },
                enabled = canSave,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(stringResource(Res.string.server_save)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AdminSettingsPreview() {
    EasyStockTheme {
        AdminSettingsContent(
            AdminSettingsState(Loaded(AdminSettings("Atelier"), loading = false)),
            {}, {}, {}, {},
        )
    }
}
