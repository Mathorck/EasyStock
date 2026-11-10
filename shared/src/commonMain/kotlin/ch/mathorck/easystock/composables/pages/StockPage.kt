package ch.mathorck.easystock.composables.pages

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.StockUiState
import ch.mathorck.easystock.viewmodels.StockViewModel
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_search
import easystock.shared.generated.resources.ic_stock
import easystock.shared.generated.resources.stock_empty_hint
import easystock.shared.generated.resources.stock_empty_title
import easystock.shared.generated.resources.stock_low
import easystock.shared.generated.resources.stock_no_results
import easystock.shared.generated.resources.stock_search_hint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Read-only list of the articles and their current stock. */
@Composable
fun StockPage() {
    val db = rememberAppDatabase()
    val viewModel = viewModel { StockViewModel(db.articleDao()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    StockContent(state = state, query = query, onQueryChange = viewModel::onQueryChange)
}

@Composable
fun StockContent(
    state: StockUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(Res.string.stock_search_hint)) },
            leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            state.loading -> Unit
            state.totalCount == 0 -> EmptyMessage(
                title = stringResource(Res.string.stock_empty_title),
                hint = stringResource(Res.string.stock_empty_hint),
            )
            state.items.isEmpty() -> EmptyMessage(
                title = stringResource(Res.string.stock_no_results, query.trim()),
                hint = null,
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.items, key = { it.article.id }) { item ->
                    StockRow(item)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun StockRow(item: ArticleStock) {
    ListItem(
        headlineContent = { Text(item.article.name) },
        supportingContent = { Text(item.article.sku) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${formatQuantity(item.stockMilli)} ${item.article.unit}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (item.isLowStock) LowStockBadge()
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun LowStockBadge() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            stringResource(Res.string.stock_low),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyMessage(title: String, hint: String?) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Icon(
                painterResource(Res.drawable.ic_stock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun sample(id: String, name: String, sku: String, stockMilli: Long, thresholdMilli: Long? = null, unit: String = "pce") =
    ArticleStock(
        Article(id = id, sku = sku, name = name, unit = unit, thresholdMilli = thresholdMilli, createdAt = 0, updatedAt = 0),
        stockMilli,
    )

private val sampleItems = listOf(
    sample("1", "Bolt M8", "BLT-8", 120_000, thresholdMilli = 50_000),
    sample("2", "Cable tie 200 mm", "CT-200", 8_000, thresholdMilli = 20_000),
    sample("3", "Duct tape", "TAPE-50", 2_500, unit = "m"),
    sample("4", "Wood screw 4x40", "SCR-440", 0, thresholdMilli = 0),
)

@Composable
private fun StockPreview(state: StockUiState, query: String = "", dark: Boolean = isSystemInDarkTheme()) {
    EasyStockTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            StockContent(state = state, query = query, onQueryChange = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StockPagePreview() {
    StockPreview(StockUiState(loading = false, items = sampleItems, totalCount = sampleItems.size), dark = false)
}

@Preview(showBackground = true)
@Composable
private fun StockPageDarkPreview() {
    StockPreview(StockUiState(loading = false, items = sampleItems, totalCount = sampleItems.size), dark = true)
}

@Preview(showBackground = true)
@Composable
private fun StockPageEmptyPreview() {
    StockPreview(StockUiState(loading = false), dark = false)
}

@Preview(showBackground = true)
@Composable
private fun StockPageNoResultsPreview() {
    StockPreview(StockUiState(loading = false, items = emptyList(), totalCount = 4), query = "nail", dark = false)
}
