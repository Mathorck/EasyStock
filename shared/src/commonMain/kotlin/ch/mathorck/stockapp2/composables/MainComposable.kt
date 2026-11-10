package ch.mathorck.stockapp2.composables

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
import ch.mathorck.stockapp2.composables.pages.StockPage
import ch.mathorck.stockapp2.composables.pages.ScanPage
import ch.mathorck.stockapp2.composables.pages.SettingsPage
import ch.mathorck.stockapp2.enums.Page

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
