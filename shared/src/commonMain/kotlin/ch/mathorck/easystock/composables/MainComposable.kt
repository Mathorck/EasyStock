package ch.mathorck.easystock.composables

import ch.mathorck.easystock.theme.EasyStockTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.app_info_reset_done
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.mathorck.easystock.auth.AppPermissions
import ch.mathorck.easystock.auth.LocalPermissions
import ch.mathorck.easystock.data.db.rememberAppDatabase
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ch.mathorck.easystock.composables.pages.StockPage
import ch.mathorck.easystock.composables.pages.ScanPage
import ch.mathorck.easystock.composables.pages.SettingsPage
import ch.mathorck.easystock.enums.Page
import ch.mathorck.easystock.nfc.NfcReaderHost
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import ch.mathorck.easystock.auth.ServerSessionEvents
import ch.mathorck.easystock.auth.rememberJoinPlatform
import ch.mathorck.easystock.reset.rememberDataReset
import easystock.shared.generated.resources.server_joined
import easystock.shared.generated.resources.server_logged_out
import easystock.shared.generated.resources.server_ok
import easystock.shared.generated.resources.server_revoked_message
import easystock.shared.generated.resources.server_revoked_title
import org.jetbrains.compose.resources.getString

@Composable
fun MainComposable() {
    val db = rememberAppDatabase()
    // Standalone (no sync_state row) until the database answers, and for good on a standalone phone
    val permissionsFlow = remember(db) { db.sessionDao().observeRole().map(AppPermissions::fromStoredRole) }
    val permissions by permissionsFlow.collectAsStateWithLifecycle(AppPermissions.Standalone)
    CompositionLocalProvider(LocalPermissions provides permissions) {
        MainContent()
    }
}

@Composable
private fun MainContent() {
    var page by rememberSaveable { mutableStateOf(Page.Scan) }
    // Runs the NFC reader while a screen waits for a tag
    NfcReaderHost()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resetDone = stringResource(Res.string.app_info_reset_done)
    val loggedOut = stringResource(Res.string.server_logged_out)
    ServerSessionWatcher(onWiped = { page = Page.Stock })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { NavBarComposable(currentPage = page, onPageSelected = { page = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (page) {
                Page.Scan -> ScanPage()
                Page.Stock -> StockPage()
                Page.Settings -> SettingsPage(
                    onDataReset = {
                        page = Page.Stock
                        scope.launch { snackbar.showSnackbar(resetDone) }
                    },
                    onLoggedOut = {
                        page = Page.Stock
                        scope.launch { snackbar.showSnackbar(loggedOut) }
                    },
                    onJoined = { address ->
                        scope.launch { snackbar.showSnackbar(getString(Res.string.server_joined, address)) }
                    },
                )
            }
        }
    }
}

/**
 * The server session outside any screen: at start, drops a token left by a join that stopped half way;
 * when the server revoked the session (the client reported it), wipes the phone like a logout and says so.
 */
@Composable
private fun ServerSessionWatcher(onWiped: () -> Unit) {
    val joinPlatform = rememberJoinPlatform()
    val dataReset = rememberDataReset()
    // Local only: reads sync_state and the session file, no network
    LaunchedEffect(joinPlatform) { runCatching { joinPlatform.join.forgetOrphanSession() } }
    val revoked by ServerSessionEvents.revoked.collectAsStateWithLifecycle()
    var showRevoked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(revoked) {
        if (!revoked) return@LaunchedEffect
        // A failed wipe is tried again at the next refused call
        runCatching { dataReset.run() }
        ServerSessionEvents.revokedHandled()
        showRevoked = true
        onWiped()
    }
    if (showRevoked) {
        AlertDialog(
            onDismissRequest = { showRevoked = false },
            title = { Text(stringResource(Res.string.server_revoked_title)) },
            text = { Text(stringResource(Res.string.server_revoked_message)) },
            confirmButton = { TextButton(onClick = { showRevoked = false }) { Text(stringResource(Res.string.server_ok)) } },
        )
    }
}

@Preview
@Composable
private fun MainComposablePreview() {
    EasyStockTheme {
        MainComposable()
    }
}

@Preview
@Composable
private fun MainComposableDarkPreview() {
    EasyStockTheme(darkTheme = true) {
        MainComposable()
    }
}
