package ch.mathorck.stockapp2.composables

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ch.mathorck.stockapp2.enums.Page
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import stockapp2.shared.generated.resources.Res
import stockapp2.shared.generated.resources.ic_scan
import stockapp2.shared.generated.resources.ic_stock
import stockapp2.shared.generated.resources.ic_settings
import stockapp2.shared.generated.resources.nav_scan
import stockapp2.shared.generated.resources.nav_stock
import stockapp2.shared.generated.resources.nav_settings

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
