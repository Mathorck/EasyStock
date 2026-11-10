package ch.mathorck.easystock.composables.pages

import ch.mathorck.easystock.theme.EasyStockTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable

@Composable
fun StockPage() {
}

@Preview(showBackground = true)
@Composable
private fun StockPagePreview() {
    EasyStockTheme {
        StockPage()
    }
}
