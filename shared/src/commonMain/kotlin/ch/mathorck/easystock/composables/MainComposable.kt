package ch.mathorck.easystock.composables

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

@Composable
fun MainComposable() {
    var page by rememberSaveable { mutableStateOf(Page.Scan) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        bottomBar = { NavBarComposable(currentPage = page, onPageSelected = { page = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (page) {
                Page.Scan -> ScanPage()
                Page.Stock -> StockPage()
                Page.Settings -> SettingsPage()
            }
        }
    }
}

@Preview
@Composable
private fun MainComposablePreview() {
    MaterialTheme {
        MainComposable()
    }
}
