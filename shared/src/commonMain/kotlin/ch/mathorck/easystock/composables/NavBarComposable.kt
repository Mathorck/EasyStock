package ch.mathorck.easystock.composables

import ch.mathorck.easystock.theme.EasyStockTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ch.mathorck.easystock.enums.Page
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_scan
import easystock.shared.generated.resources.ic_stock
import easystock.shared.generated.resources.ic_settings
import easystock.shared.generated.resources.nav_scan
import easystock.shared.generated.resources.nav_stock
import easystock.shared.generated.resources.nav_settings

@Composable
fun NavBarComposable(currentPage: Page, onPageSelected: (Page) -> Unit) {
    NavigationBar {
        Page.entries.forEach { page ->
            val label = stringResource(page.label())
            NavigationBarItem(
                selected = page == currentPage,
                onClick = { onPageSelected(page) },
                icon = { Icon(painterResource(page.icon()), contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

private fun Page.icon(): DrawableResource = when (this) {
    Page.Stock -> Res.drawable.ic_stock
    Page.Scan -> Res.drawable.ic_scan
    Page.Settings -> Res.drawable.ic_settings
}

private fun Page.label(): StringResource = when (this) {
    Page.Stock -> Res.string.nav_stock
    Page.Scan -> Res.string.nav_scan
    Page.Settings -> Res.string.nav_settings
}

@Preview
@Composable
private fun NavBarComposablePreview() {
    EasyStockTheme {
        NavBarComposable(currentPage = Page.Scan, onPageSelected = {})
    }
}
