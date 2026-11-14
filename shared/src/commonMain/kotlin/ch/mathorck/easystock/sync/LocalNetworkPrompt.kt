package ch.mathorck.easystock.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ch.mathorck.easystock.server.rememberLocalNetworkAccess
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.local_network_allow
import easystock.shared.generated.resources.local_network_not_now
import easystock.shared.generated.resources.local_network_open_settings
import easystock.shared.generated.resources.local_network_prompt_message
import easystock.shared.generated.resources.local_network_settings_hint
import easystock.shared.generated.resources.local_network_title
import org.jetbrains.compose.resources.stringResource

/**
 * A connected phone without the local network permission (Android 17+, e.g. after an app update):
 * background sync can't ask, so the app asks when it starts. "Not now" is not asked again until the
 * next start; the Account screen keeps the way out next to the sync error. Once allowed, a sync starts.
 */
@Composable
internal fun LocalNetworkPrompt(engine: SyncEngine) {
    var missing by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var answers by remember { mutableIntStateOf(0) }
    val access = rememberLocalNetworkAccess { answers++ }
    fun refresh() {
        val now = access.isMissing()
        if (missing && !now) engine.requestSync()
        missing = now
        settings = now && access.mustOpenSettings()
    }
    LaunchedEffect(answers) { if (answers > 0) refresh() }
    LifecycleResumeEffect(access) {
        refresh()
        onPauseOrDispose {}
    }
    if (!missing || dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(Res.string.local_network_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.local_network_prompt_message))
                if (settings) Text(stringResource(Res.string.local_network_settings_hint))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (settings) access.openSettings() else access.request() }) {
                Text(stringResource(if (settings) Res.string.local_network_open_settings else Res.string.local_network_allow))
            }
        },
        dismissButton = { TextButton(onClick = { dismissed = true }) { Text(stringResource(Res.string.local_network_not_now)) } },
    )
}
