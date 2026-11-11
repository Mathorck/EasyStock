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

@Composable
fun MainComposable() {
    var page by rememberSaveable { mutableStateOf(Page.Scan) }
    // Runs the NFC reader while a screen waits for a tag
    NfcReaderHost()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resetDone = stringResource(Res.string.app_info_reset_done)

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
                )
            }
        }
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
