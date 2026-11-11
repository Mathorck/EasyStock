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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import ch.mathorck.easystock.composables.attributes.AttributeRow
import ch.mathorck.easystock.auth.AppPermissions
import ch.mathorck.easystock.auth.ArticleDetailGates
import ch.mathorck.easystock.auth.LocalPermissions
import ch.mathorck.easystock.auth.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import easystock.shared.generated.resources.article_history_author
import easystock.shared.generated.resources.article_image_description
import ch.mathorck.easystock.composables.nfc.ArticleTagSection
import ch.mathorck.easystock.composables.nfc.TagLinkSheet
import ch.mathorck.easystock.data.db.TagTarget
import ch.mathorck.easystock.data.db.asTagStore
import ch.mathorck.easystock.viewmodels.TagLinkViewModel
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
import ch.mathorck.easystock.viewmodels.HistoryRow
import ch.mathorck.easystock.viewmodels.InfoFlagRow
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
import easystock.shared.generated.resources.article_history_info_fixed
import easystock.shared.generated.resources.article_history_info_flagged
import easystock.shared.generated.resources.article_history_info_flagged_note
import easystock.shared.generated.resources.article_info_fixed_action
import easystock.shared.generated.resources.article_info_fixed_cancel
import easystock.shared.generated.resources.article_info_fixed_confirm
import easystock.shared.generated.resources.article_info_fixed_confirm_text
import easystock.shared.generated.resources.article_info_fixed_confirm_title
import easystock.shared.generated.resources.article_info_flag_no_note
import easystock.shared.generated.resources.article_info_flag_source
import easystock.shared.generated.resources.article_info_flag_title
import easystock.shared.generated.resources.article_info_flag_unit
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
import easystock.shared.generated.resources.article_section_properties
import easystock.shared.generated.resources.article_sku_label
import easystock.shared.generated.resources.article_threshold_value
import easystock.shared.generated.resources.article_total_stock
import easystock.shared.generated.resources.article_unit_label
import easystock.shared.generated.resources.ic_arrow_back
import easystock.shared.generated.resources.ic_edit
import easystock.shared.generated.resources.ic_flag
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
        StockActionViewModel(articleId, db.stockActionDao(), db.articleDetailDao(), db.stockDao(), db.attributeDao())
    }
    val units by actions.units.collectAsStateWithLifecycle()
    val actionState by actions.sheet.collectAsStateWithLifecycle()
    val tagLink = viewModel(key = "tag-link-$articleId") { TagLinkViewModel(TagTarget.OfArticle(articleId), db.tagDao().asTagStore()) }
    val tagState by tagLink.state.collectAsStateWithLifecycle()
    val gates = ArticleDetailGates.of(LocalPermissions.current)

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
        onEditUnit = actions::openEditUnit,
        tagRemoveFailed = tagState.removeFailed,
        onLinkTag = tagLink::start,
        onRemoveTag = tagLink::removeTag,
        onMarkInfoFixed = viewModel::markInfoFixed,
        modifier = modifier,
        gates = gates,
    )

    // Also checked here: the role can change while a sheet is open
    if (tagState.waiting && gates.linkTag) {
        TagLinkSheet(
            state = tagState,
            currentTagId = state.article?.barcode,
            targetName = state.article?.name.orEmpty(),
            onTag = tagLink::onTag,
            onConfirmMove = tagLink::confirmMove,
            onCancelMove = tagLink::cancelMove,
            onDismiss = tagLink::cancel,
        )
    }

    val unit = state.article?.unit
    val sheet = actionState
    if (sheet != null && unit != null && gates.stockActions) {
        StockActionSheet(
            state = sheet,
            unit = unit,
            onFormChange = actions::update,
            onDismiss = actions::close,
            onSave = actions::save,
        )
    }

    editState?.takeIf { gates.edit }?.let { edit ->
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
    onEditUnit: (UnitRow) -> Unit = {},
    tagRemoveFailed: Boolean = false,
    onLinkTag: () -> Unit = {},
    onRemoveTag: () -> Unit = {},
    onMarkInfoFixed: (lineId: String) -> Unit = {},
    /** What the role can see and do, everything in standalone mode. */
    gates: ArticleDetailGates = ArticleDetailGates.of(AppPermissions.Standalone),
) {
    val article = state.article
    // Line id of the flag waiting for the "mark as fixed" confirmation
    var confirmFixed by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(article?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.article_back))
                }
            },
            actions = {
                if (article != null && gates.edit) {
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
            if (gates.edit) {
                item(key = "image") {
                    ArticleImageEditor(
                        path = article.imagePath,
                        onImported = onImageChange,
                        onRemove = { onImageChange(null) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else if (article.imagePath != null) {
                item(key = "image") { ReadOnlyImage(article.imagePath) }
            }
            item(key = "header") { Header(article, state.categoryName) }
            if (gates.infoFlags) {
                items(state.infoFlags, key = { "info-flag-${it.lineId}" }) { flag ->
                    InfoFlagCard(flag, onFixed = if (gates.markInfoFixed) ({ confirmFixed = flag.lineId }) else null)
                }
            }
            if (state.properties.isNotEmpty()) {
                item(key = "properties") { PropertiesSection(state.properties) }
            }
            item(key = "total") { TotalStock(state, article.unit) }
            // Without the right to link, the tag is shown only when there is one
            if (gates.linkTag || article.barcode != null) {
                item(key = "tag") {
                    ArticleTagSection(
                        tagId = article.barcode,
                        removeFailed = tagRemoveFailed,
                        onLink = onLinkTag,
                        onRemove = onRemoveTag,
                        editable = gates.linkTag,
                    )
                }
            }

            if (article.tracking == Tracking.SERIAL) {
                item(key = "units-title") { SectionTitle(stringResource(Res.string.stock_action_section_units)) }
                if (units.isEmpty()) {
                    item(key = "units-empty") { EmptyHint(stringResource(Res.string.stock_action_units_empty)) }
                } else {
                    items(units, key = { "unit-${it.itemId}" }) { unit ->
                        UnitActionRow(
                            unit = unit,
                            onEdit = { onEditUnit(unit) },
                            onMove = { onStockAction(StockActionForm(kind = StockActionKind.MOVE_UNIT, itemId = unit.itemId)) },
                            onAction = { action -> onUnitAction(unit, action) },
                            canEdit = gates.edit,
                            canAct = gates.stockActions,
                        )
                    }
                }
                if (gates.stockActions) {
                    item(key = "units-add") {
                        StockActionButton(stringResource(Res.string.stock_action_add_unit)) {
                            onStockAction(StockActionForm(kind = StockActionKind.ADD_UNIT))
                        }
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
                            actions = gates.stockActions,
                        )
                    }
                }
                if (gates.stockActions) {
                    item(key = "locations-add") {
                        StockActionButton(stringResource(Res.string.stock_action_add_to_location)) {
                            onStockAction(StockActionForm(kind = StockActionKind.ADD))
                        }
                    }
                }
            }

            // Guests: no history section
            if (gates.history) {
                item(key = "history-title") { SectionTitle(stringResource(Res.string.article_section_history)) }
                if (state.history.isEmpty()) {
                    item(key = "history-empty") { EmptyHint(stringResource(Res.string.article_history_empty)) }
                } else {
                    items(state.history, key = { it.key }) { row ->
                        when (row) {
                            is HistoryRow.Movement -> MovementItem(row.movement, article.unit)
                            is HistoryRow.InfoFlagged -> InfoHistoryItem(row.at, row.flag, fixed = false)
                            is HistoryRow.InfoFixed -> InfoHistoryItem(row.at, row.flag, fixed = true)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    confirmFixed?.let { lineId ->
        AlertDialog(
            onDismissRequest = { confirmFixed = null },
            title = { Text(stringResource(Res.string.article_info_fixed_confirm_title)) },
            text = { Text(stringResource(Res.string.article_info_fixed_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFixed = null
                    onMarkInfoFixed(lineId)
                }) { Text(stringResource(Res.string.article_info_fixed_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFixed = null }) { Text(stringResource(Res.string.article_info_fixed_cancel)) }
            },
        )
    }
}

/** The photo without the change buttons, when the role cannot edit the article. */
@Composable
private fun ReadOnlyImage(path: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        ArticleImage(
            path = path,
            contentDescription = stringResource(Res.string.article_image_description),
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * An open "info to fix" flag from a finished review: the note, where and when it was raised, and a "fixed" button
 * (none when [onFixed] is null: the role cannot change the article).
 */
@Composable
private fun InfoFlagCard(flag: InfoFlagRow, onFixed: (() -> Unit)?) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)) {
            Icon(painterResource(Res.drawable.ic_flag), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.article_info_flag_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    flag.note ?: stringResource(Res.string.article_info_flag_no_note),
                    style = MaterialTheme.typography.bodyLarge,
                )
                flag.serialNumber?.let {
                    Text(stringResource(Res.string.article_info_flag_unit, it), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(Res.string.article_info_flag_source, flag.flaggedAt, flag.location),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (onFixed != null) TextButton(onClick = onFixed) { Text(stringResource(Res.string.article_info_fixed_action)) }
        }
    }
}

/** History entry of an "info to fix" flag: raised (with its note) or fixed. */
@Composable
private fun InfoHistoryItem(at: String, flag: InfoFlagRow, fixed: Boolean) {
    val headline = when {
        fixed -> stringResource(Res.string.article_history_info_fixed)
        flag.note != null -> stringResource(Res.string.article_history_info_flagged_note, flag.note)
        else -> stringResource(Res.string.article_history_info_flagged)
    }
    ListItem(
        overlineContent = { Text(at) },
        headlineContent = { Text(headline) },
        supportingContent = {
            Column {
                Text(flag.location)
                flag.serialNumber?.let { Text(stringResource(Res.string.article_info_flag_unit, it)) }
                // The fixed entry recalls which note it closes
                if (fixed && flag.note != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(flag.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = {
            Icon(painterResource(Res.drawable.ic_flag), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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

/** The attribute values of the article, read-only, one row per value. */
@Composable
private fun PropertiesSection(properties: List<AttributeRow>) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(Res.string.article_section_properties))
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            properties.forEach { InfoRow(it.label, it.value) }
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
                // Server mode only: standalone movements have no author
                movement.author?.let {
                    Text(stringResource(Res.string.article_history_author, it), style = MaterialTheme.typography.bodySmall)
                }
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

private val previewFlag = InfoFlagRow("f1", "The box must be changed", "Workshop › Shelf A", null, "2026-11-09 10:00")

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
    infoFlags = listOf(previewFlag),
    history = listOf(
        HistoryRow.Movement(MovementRow("m3", "2026-11-10 14:57", MovementReason.CONSUMPTION, "Workshop › Shelf A", -10_000, "Job 12")),
        HistoryRow.InfoFlagged(previewFlag, previewFlag.flaggedAt),
        HistoryRow.Movement(MovementRow("m2", "2026-11-09 08:12", MovementReason.TRANSFER, "Van", 2_500, null)),
        HistoryRow.Movement(MovementRow("m1", "2026-11-01 17:30", MovementReason.PURCHASE, "Workshop › Shelf A", 50_000, "Order #42")),
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

/** The article screen as a server [role] sees it. */
@Composable
private fun ArticleDetailRolePreview(role: Role) {
    EasyStockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val authored = previewState.copy(
                history = previewState.history.map { row ->
                    if (row is HistoryRow.Movement) HistoryRow.Movement(row.movement.copy(author = "Anna")) else row
                },
            )
            ArticleDetailContent(
                state = authored,
                onBack = {},
                onEdit = {},
                gates = ArticleDetailGates.of(AppPermissions.connectedAs(role)),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleDetailViewerPreview() {
    ArticleDetailRolePreview(Role.VIEWER)
}

@Preview(showBackground = true)
@Composable
private fun ArticleDetailGuestPreview() {
    ArticleDetailRolePreview(Role.GUEST)
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
