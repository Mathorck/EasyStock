package ch.mathorck.easystock.composables.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.mathorck.easystock.push.PushDistributor
import ch.mathorck.easystock.push.PushStatus
import ch.mathorck.easystock.push.PushUiState
import ch.mathorck.easystock.push.pushUiStateOf
import ch.mathorck.easystock.push.rememberPushRegistrar
import ch.mathorck.easystock.theme.EasyStockTheme
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.push_cancel
import easystock.shared.generated.resources.push_no_distributor
import easystock.shared.generated.resources.push_off
import easystock.shared.generated.resources.push_on_active
import easystock.shared.generated.resources.push_on_failed
import easystock.shared.generated.resources.push_on_waiting
import easystock.shared.generated.resources.push_pick_message
import easystock.shared.generated.resources.push_pick_title
import easystock.shared.generated.resources.push_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The optional UnifiedPush row of the Account screen: offers the installed distributor apps (the user
 * picks one), or says the 15-minute background sync is used. Nothing where push is not available (iOS).
 */
@Composable
internal fun PushRow(serverUrl: String) {
    val registrar = rememberPushRegistrar() ?: return
    val scope = rememberCoroutineScope()
    val record by registrar.record.collectAsStateWithLifecycle()
    // Read again on each resume: the user may have installed or removed a distributor meanwhile
    var distributors by remember { mutableStateOf(registrar.distributors()) }
    LifecycleResumeEffect(registrar) {
        distributors = registrar.distributors()
        onPauseOrDispose {}
    }
    PushRowContent(
        state = pushUiStateOf(distributors, record, serverUrl),
        onEnable = { scope.launch { registrar.enable(it.id) } },
        onDisable = { scope.launch { registrar.disable() } },
    )
}

@Composable
internal fun PushRowContent(state: PushUiState, onEnable: (PushDistributor) -> Unit, onDisable: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val on = state is PushUiState.On
    val toggle: (() -> Unit)? = when (state) {
        PushUiState.NoDistributor -> null
        is PushUiState.On -> onDisable
        // The user always picks: a dialog, even with one distributor
        is PushUiState.Off -> ({ picking = true })
    }
    ListItem(
        headlineContent = { Text(stringResource(Res.string.push_title)) },
        supportingContent = {
            Text(
                when (state) {
                    PushUiState.NoDistributor -> stringResource(Res.string.push_no_distributor)
                    is PushUiState.Off -> stringResource(Res.string.push_off)
                    is PushUiState.On -> when (state.status) {
                        PushStatus.WAITING -> stringResource(Res.string.push_on_waiting, state.distributor.label)
                        PushStatus.ACTIVE -> stringResource(Res.string.push_on_active, state.distributor.label)
                        PushStatus.FAILED -> stringResource(Res.string.push_on_failed, state.distributor.label)
                    }
                },
            )
        },
        trailingContent = toggle?.let { { Switch(checked = on, onCheckedChange = { toggle() }) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = if (toggle != null) Modifier.clickable(onClick = toggle) else Modifier,
    )
    if (picking && state is PushUiState.Off) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(Res.string.push_pick_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.push_pick_message), style = MaterialTheme.typography.bodyMedium)
                    state.distributors.forEach { distributor ->
                        ListItem(
                            headlineContent = { Text(distributor.label) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.clickable {
                                picking = false
                                onEnable(distributor)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.push_cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PushRowPreview() {
    EasyStockTheme {
        Column {
            PushRowContent(PushUiState.NoDistributor, {}, {})
            PushRowContent(PushUiState.Off(listOf(PushDistributor("io.heckel.ntfy", "ntfy"))), {}, {})
            PushRowContent(PushUiState.On(PushDistributor("io.heckel.ntfy", "ntfy"), PushStatus.ACTIVE), {}, {})
        }
    }
}
