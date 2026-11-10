@file:OptIn(ExperimentalMaterial3Api::class)

package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.pages.LowStockBadge
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.db.UnitAction
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.images.rememberArticleImageStore
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.ArticleDetailUiState
import ch.mathorck.easystock.viewmodels.ArticleDetailViewModel
import ch.mathorck.easystock.viewmodels.LocationStockRow
import ch.mathorck.easystock.viewmodels.MovementRow
import ch.mathorck.easystock.viewmodels.StockActionForm
import ch.mathorck.easystock.viewmodels.StockActionKind
import ch.mathorck.easystock.viewmodels.StockActionViewModel
import ch.mathorck.easystock.viewmodels.UnitRow
import ch.mathorck.easystock.viewmodels.formatSignedQuantity
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.article_back
import easystock.shared.generated.resources.article_category_label
import easystock.shared.generated.resources.article_description_label
import easystock.shared.generated.resources.article_edit
import easystock.shared.generated.resources.article_history_empty
import easystock.shared.generated.resources.article_locations_empty
import easystock.shared.generated.resources.article_reason_consumption
import easystock.shared.generated.resources.article_reason_inventory
import easystock.shared.generated.resources.article_reason_loan
import easystock.shared.generated.resources.article_reason_loss
import easystock.shared.generated.resources.article_reason_purchase
import easystock.shared.generated.resources.article_reason_return
import easystock.shared.generated.resources.article_reason_transfer
import easystock.shared.generated.resources.article_section_history
import easystock.shared.generated.resources.article_section_locations
import easystock.shared.generated.resources.article_sku_label
import easystock.shared.generated.resources.article_threshold_value
import easystock.shared.generated.resources.article_total_stock
import easystock.shared.generated.resources.article_unit_label
import easystock.shared.generated.resources.ic_arrow_back
import easystock.shared.generated.resources.ic_edit
import easystock.shared.generated.resources.stock_action_add_to_location
import easystock.shared.generated.resources.stock_action_add_unit
import easystock.shared.generated.resources.stock_action_section_units
import easystock.shared.generated.resources.stock_action_units_empty
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Detail of one article: stock per location (or serial units) and movement history.
 * The article can be edited, stock is added, removed and moved from the location rows.
 * [onBack] runs on the top-bar arrow, the system back, and when the article gets deleted.
 */
@Composable
fun ArticleDetailScreen(articleId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val images = rememberArticleImageStore()
    // Keyed: the ViewModel store is shared, each article needs its own instance
    val viewModel = viewModel(key = "article-detail-$articleId") {
        ArticleDetailViewModel(articleId, db.articleDetailDao(), db.categoryDao(), db.attributeDao(), images)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val actions = viewModel(key = "stock-actions-$articleId") {
        StockActionViewModel(articleId, db.stockActionDao(), db.articleDetailDao(), db.stockDao())
    }
    val units by actions.units.collectAsStateWithLifecycle()
    val actionState by actions.sheet.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    ArticleDetailContent(
        state = state,
        onBack = onBack,
        onEdit = viewModel::startEdit,
        onImageChange = viewModel::setImage,
        units = units,
        onStockAction = actions::open,
        onUnitAction = actions::openUnitAction,
        modifier = modifier,
    )

    val unit = state.article?.unit
    val sheet = actionState
    if (sheet != null && unit != null) {
        StockActionSheet(
            state = sheet,
            unit = unit,
            onFormChange = actions::update,
            onDismiss = actions::close,
            onSave = actions::save,
        )
    }

    editState?.let { edit ->
        ArticleEditSheet(
            state = edit,
            onFormChange = viewModel::updateEdit,
            onImageImported = viewModel::onEditImageImported,
            onDismiss = viewModel::cancelEdit,
            onSave = viewModel::save,
        )
    }
}

@Composable
fun ArticleDetailContent(
    state: ArticleDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onImageChange: (String?) -> Unit = {},
    units: List<UnitRow> = emptyList(),
    onStockAction: (StockActionForm) -> Unit = {},
    onUnitAction: (UnitRow, UnitAction) -> Unit = { _, _ -> },
) {
    val article = state.article
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(article?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.article_back))
                }
            },
            actions = {
                if (article != null) {
                    IconButton(onClick = onEdit) {
                        Icon(painterResource(Res.drawable.ic_edit), contentDescription = stringResource(Res.string.article_edit))
                    }
                }
            },
            // The page Scaffold already applies the system bar insets
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (article == null) return@Column
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "image") {
                ArticleImageEditor(
                    path = article.imagePath,
                    onImported = onImageChange,
                    onRemove = { onImageChange(null) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "header") { Header(article, state.categoryName) }
            item(key = "total") { TotalStock(state, article.unit) }

            if (article.tracking == Tracking.SERIAL) {
                item(key = "units-title") { SectionTitle(stringResource(Res.string.stock_action_section_units)) }
                if (units.isEmpty()) {
                    item(key = "units-empty") { EmptyHint(stringResource(Res.string.stock_action_units_empty)) }
                } else {
                    items(units, key = { "unit-${it.itemId}" }) { unit ->
                        UnitActionRow(
                            unit = unit,
                            onMove = { onStockAction(StockActionForm(kind = StockActionKind.MOVE_UNIT, itemId = unit.itemId)) },
                            onAction = { action -> onUnitAction(unit, action) },
                        )
                    }
                }
                item(key = "units-add") {
                    StockActionButton(stringResource(Res.string.stock_action_add_unit)) {
                        onStockAction(StockActionForm(kind = StockActionKind.ADD_UNIT))
                    }
                }
            } else {
                item(key = "locations-title") { SectionTitle(stringResource(Res.string.article_section_locations)) }
                if (state.locations.isEmpty()) {
                    item(key = "locations-empty") { EmptyHint(stringResource(Res.string.article_locations_empty)) }
                } else {
                    items(state.locations, key = { "location-${it.locationId}" }) { row ->
                        LocationActionRow(
                            row = row,
                            unit = article.unit,
                            onMove = { onStockAction(StockActionForm(kind = StockActionKind.MOVE, sourceLocationId = row.locationId)) },
                            onRemove = { onStockAction(StockActionForm(kind = StockActionKind.REMOVE, sourceLocationId = row.locationId)) },
                            onAdd = { onStockAction(StockActionForm(kind = StockActionKind.ADD, sourceLocationId = row.locationId)) },
                        )
                    }
                }
                item(key = "locations-add") {
                    StockActionButton(stringResource(Res.string.stock_action_add_to_location)) {
                        onStockAction(StockActionForm(kind = StockActionKind.ADD))
                    }
                }
            }

            item(key = "history-title") { SectionTitle(stringResource(Res.string.article_section_history)) }
            if (state.movements.isEmpty()) {
                item(key = "history-empty") { EmptyHint(stringResource(Res.string.article_history_empty)) }
            } else {
                items(state.movements, key = { "movement-${it.id}" }) { movement ->
                    MovementItem(movement, article.unit)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(article: Article, categoryName: String?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        article.sku?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(Res.string.article_sku_label), it) }
        categoryName?.let { InfoRow(stringResource(Res.string.article_category_label), it) }
        InfoRow(stringResource(Res.string.article_unit_label), article.unit)
        article.description?.takeIf { it.isNotBlank() }?.let {
            InfoRow(stringResource(Res.string.article_description_label), it)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TotalStock(state: ArticleDetailUiState, unit: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.article_total_stock),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatQuantity(state.totalMilli)} $unit",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (state.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                state.article?.thresholdMilli?.let { threshold ->
                    Text(
                        stringResource(Res.string.article_threshold_value, "${formatQuantity(threshold)} $unit"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.isLowStock) LowStockBadge()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MovementItem(movement: MovementRow, unit: String) {
    ListItem(
        overlineContent = { Text(movement.occurredAt) },
        headlineContent = { Text(stringResource(movement.reason.historyLabel)) },
        supportingContent = {
            Column {
                Text(movement.location)
                movement.comment?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            Text(
                "${formatSignedQuantity(movement.quantityMilli)} $unit",
                style = MaterialTheme.typography.titleSmall,
                color = if (movement.quantityMilli < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

internal val MovementReason.historyLabel: StringResource
    get() = when (this) {
        MovementReason.PURCHASE -> Res.string.article_reason_purchase
        MovementReason.CONSUMPTION -> Res.string.article_reason_consumption
        MovementReason.INVENTORY -> Res.string.article_reason_inventory
        MovementReason.TRANSFER -> Res.string.article_reason_transfer
        MovementReason.LOAN -> Res.string.article_reason_loan
        MovementReason.RETURN -> Res.string.article_reason_return
        MovementReason.LOSS -> Res.string.article_reason_loss
    }

// ---------- Previews ----------

private val previewState = ArticleDetailUiState(
    loading = false,
    article = Article(
        id = "a1", sku = "BLT-8", name = "Bolt M8", description = "Zinc plated, DIN 933", unit = "pce",
        thresholdMilli = 50_000, createdAt = 0, updatedAt = 0,
    ),
    categoryName = "Hardware",
    totalMilli = 42_500,
    locations = listOf(
        LocationStockRow("l2", "Workshop › Shelf A", 40_000),
        LocationStockRow("l3", "Van", 2_500),
    ),
    movements = listOf(
        MovementRow("m3", "2026-11-10 14:57", MovementReason.CONSUMPTION, "Workshop › Shelf A", -10_000, "Job 12"),
        MovementRow("m2", "2026-11-09 08:12", MovementReason.TRANSFER, "Van", 2_500, null),
        MovementRow("m1", "2026-11-01 17:30", MovementReason.PURCHASE, "Workshop › Shelf A", 50_000, "Order #42"),
    ),
)

@Composable
private fun ArticleDetailPreview(dark: Boolean) {
    EasyStockTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ArticleDetailContent(state = previewState, onBack = {}, onEdit = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleDetailLightPreview() {
    ArticleDetailPreview(dark = false)
}

@Preview(showBackground = true)
@Composable
private fun ArticleDetailDarkPreview() {
    ArticleDetailPreview(dark = true)
}
