package ch.mathorck.easystock.composables.pages

import ch.mathorck.easystock.auth.LocalPermissions
import ch.mathorck.easystock.auth.StockListGates
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.saveable.rememberSaveable
import ch.mathorck.easystock.composables.article.ArticleDetailScreen
import ch.mathorck.easystock.composables.article.ArticleFormDialog
import ch.mathorck.easystock.composables.article.ArticleThumbnail
import ch.mathorck.easystock.images.rememberArticleImageStore
import androidx.compose.runtime.LaunchedEffect
import ch.mathorck.easystock.viewmodels.NewArticleViewModel
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.stock_add_entry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import ch.mathorck.easystock.composables.LocationIcon
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.theme.EasyStockTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.StockFilters
import ch.mathorck.easystock.viewmodels.StockLevelFilter
import ch.mathorck.easystock.viewmodels.TagFilter
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.data.model.AttributeDefinition
import easystock.shared.generated.resources.ic_close
import easystock.shared.generated.resources.ic_nfc
import easystock.shared.generated.resources.stock_filter_active
import easystock.shared.generated.resources.stock_filter_all_types
import easystock.shared.generated.resources.stock_filter_any_attribute
import easystock.shared.generated.resources.stock_filter_attribute
import easystock.shared.generated.resources.stock_filter_clear_attribute
import easystock.shared.generated.resources.stock_filter_clear_tag
import easystock.shared.generated.resources.stock_filter_clear_type
import easystock.shared.generated.resources.stock_filter_consumables
import easystock.shared.generated.resources.stock_filter_has_attribute
import easystock.shared.generated.resources.stock_filter_out_of_stock
import easystock.shared.generated.resources.stock_filter_tag
import easystock.shared.generated.resources.stock_filter_tag_any
import easystock.shared.generated.resources.stock_filter_tag_with
import easystock.shared.generated.resources.stock_filter_tag_without
import easystock.shared.generated.resources.stock_filter_tools
import easystock.shared.generated.resources.stock_filter_type
import easystock.shared.generated.resources.stock_no_results_filtered
import ch.mathorck.easystock.viewmodels.StockUiState
import ch.mathorck.easystock.viewmodels.StockViewModel
import ch.mathorck.easystock.viewmodels.toLocationTree
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_arrow_drop_down
import easystock.shared.generated.resources.ic_cancel
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.ic_search
import easystock.shared.generated.resources.ic_stock
import easystock.shared.generated.resources.stock_empty_hint
import easystock.shared.generated.resources.stock_empty_title
import easystock.shared.generated.resources.stock_filter_all_categories
import easystock.shared.generated.resources.stock_filter_all_locations
import easystock.shared.generated.resources.stock_filter_category
import easystock.shared.generated.resources.stock_filter_clear_all
import easystock.shared.generated.resources.stock_filter_clear_category
import easystock.shared.generated.resources.stock_filter_clear_location
import easystock.shared.generated.resources.stock_filter_location
import easystock.shared.generated.resources.stock_filter_low_stock
import easystock.shared.generated.resources.stock_filter_no_results
import easystock.shared.generated.resources.stock_low
import easystock.shared.generated.resources.stock_no_results
import easystock.shared.generated.resources.stock_search_hint
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import ch.mathorck.easystock.sync.rememberSyncEngine
import ch.mathorck.easystock.alerts.AlertNavigation
import ch.mathorck.easystock.alerts.AlertTarget
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Read-only list of the articles and their current stock, with search and filters. */
@Composable
fun StockPage() {
    val db = rememberAppDatabase()
    val viewModel = viewModel { StockViewModel(db.articleDao(), db.stockDao(), db.categoryDao(), db.attributeDao()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val images = rememberArticleImageStore()
    val newArticleViewModel = viewModel { NewArticleViewModel(db.articleFormDao(), db.categoryDao(), db.attributeDao(), images) }
    val newArticleState by newArticleViewModel.state.collectAsStateWithLifecycle()
    val createdArticleId by newArticleViewModel.created.collectAsStateWithLifecycle()
    // Article shown in the detail screen instead of the list. The search and filters stay in the ViewModel.
    var selectedArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    // Hoisted so the list keeps its scroll position while the detail is shown
    val listState = rememberLazyListState()
    // A tapped low-stock notification: its article, or the low-stock articles
    val alertTarget by AlertNavigation.target.collectAsStateWithLifecycle()
    LaunchedEffect(alertTarget) {
        when (val target = alertTarget) {
            is AlertTarget.Article -> selectedArticleId = target.articleId
            AlertTarget.LowStock -> {
                selectedArticleId = null
                viewModel.onFiltersChange(StockFilters(stockLevel = StockLevelFilter.LOW))
            }
            null -> return@LaunchedEffect
        }
        AlertNavigation.consumed()
    }
    // A saved new article opens its detail screen
    LaunchedEffect(createdArticleId) {
        createdArticleId?.let {
            selectedArticleId = it
            newArticleViewModel.consumeCreated()
        }
    }
    Box(Modifier.fillMaxSize()) {
        val articleId = selectedArticleId
        if (articleId != null) {
            ArticleDetailScreen(articleId = articleId, onBack = { selectedArticleId = null })
        } else {
            SyncRefreshBox {
                StockContent(
                    state = state,
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onFiltersChange = viewModel::onFiltersChange,
                    onClearFilters = viewModel::onClearFilters,
                    onArticleClick = { selectedArticleId = it },
                    listState = listState,
                    // Keeps the last row above the FAB
                    contentPadding = PaddingValues(bottom = 88.dp),
                )
            }
            if (StockListGates.of(LocalPermissions.current).createArticle) {
                FloatingActionButton(
                    onClick = newArticleViewModel::start,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.stock_add_entry))
                }
            }
        }
    }
    newArticleState?.let { form ->
        ArticleFormDialog(
            state = form,
            onFormChange = newArticleViewModel::update,
            onImageImported = newArticleViewModel::onImageImported,
            onDismiss = newArticleViewModel::cancel,
            onSave = newArticleViewModel::save,
        )
    }
}

/** Pull to refresh runs a sync, only when connected to a server: standalone, the list shows as is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncRefreshBox(content: @Composable () -> Unit) {
    val engine = if (LocalPermissions.current.connected) rememberSyncEngine() else null
    if (engine == null) {
        content()
        return
    }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                try {
                    engine.sync()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { content() }
}

private enum class StockPicker { Location, Category, Type, Tag, Attribute }

/** One entry of a picker sheet. A null [id] is the "All" entry. */
private data class PickerOption(
    val id: String?,
    val label: String,
    val depth: Int = 0,
    val icon: String? = null,
    val color: String? = null,
    /** True for location entries: show the location icon (the default one when [icon] is null). */
    val showIcon: Boolean = false,
)

/**
 * @param contentPadding padding of the list, e.g. to keep the last row above a FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockContent(
    state: StockUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (StockFilters) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
    onArticleClick: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
) {
    var picker by remember { mutableStateOf<StockPicker?>(null) }
    val filters = state.filters
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            // One line at phone width: the fr and de hints are long and would wrap, making the field taller.
            placeholder = {
                Text(stringResource(Res.string.stock_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        // Kept while loading a location change, so the chips don't flicker.
        if (state.totalCount > 0) {
            FilterRow(
                state = state,
                onOpenPicker = { picker = it },
                onFiltersChange = onFiltersChange,
                onClearFilters = onClearFilters,
            )
        }
        when {
            state.loading -> Unit
            state.totalCount == 0 -> EmptyMessage(
                title = stringResource(Res.string.stock_empty_title),
                hint = stringResource(Res.string.stock_empty_hint),
            )
            state.items.isEmpty() -> {
                val labels = activeFilterLabels(state)
                EmptyMessage(
                    title = when {
                        query.isBlank() -> stringResource(Res.string.stock_filter_no_results)
                        labels.isEmpty() -> stringResource(Res.string.stock_no_results, query.trim())
                        else -> stringResource(Res.string.stock_no_results_filtered, query.trim())
                    },
                    hint = labels.takeIf { it.isNotEmpty() }
                        ?.let { stringResource(Res.string.stock_filter_active, it.joinToString(" · ")) },
                    action = if (filters.isActive) {
                        { TextButton(onClick = onClearFilters) { Text(stringResource(Res.string.stock_filter_clear_all)) } }
                    } else {
                        null
                    },
                )
            }
            else -> LazyColumn(state = listState, contentPadding = contentPadding) {
                items(state.items, key = { it.article.id }) { item ->
                    Box(Modifier.clickable { onArticleClick(item.article.id) }) { StockRow(item, state.attributeLines[item.article.id]) }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    val dismiss = { picker = null }
    when (picker) {
        StockPicker.Location -> PickerSheet(
            title = stringResource(Res.string.stock_filter_location),
            options = listOf(PickerOption(null, stringResource(Res.string.stock_filter_all_locations))) +
                state.locations.map {
                    PickerOption(it.location.id, it.location.name, it.depth, it.location.icon, it.location.color, showIcon = true)
                },
            selectedId = filters.locationId,
            onSelect = { onFiltersChange(filters.copy(locationId = it)) },
            onDismiss = dismiss,
        )
        StockPicker.Category -> PickerSheet(
            title = stringResource(Res.string.stock_filter_category),
            options = listOf(PickerOption(null, stringResource(Res.string.stock_filter_all_categories))) +
                state.categories.map { PickerOption(it.id, it.name) },
            selectedId = filters.categoryId,
            onSelect = { onFiltersChange(filters.copy(categoryId = it)) },
            onDismiss = dismiss,
        )
        StockPicker.Type -> PickerSheet(
            title = stringResource(Res.string.stock_filter_type),
            options = listOf(PickerOption(null, stringResource(Res.string.stock_filter_all_types))) +
                Tracking.entries.map { PickerOption(it.name, trackingLabel(it)) },
            selectedId = filters.tracking?.name,
            onSelect = { id -> onFiltersChange(filters.withTracking(Tracking.entries.firstOrNull { it.name == id })) },
            onDismiss = dismiss,
        )
        StockPicker.Tag -> PickerSheet(
            title = stringResource(Res.string.stock_filter_tag),
            options = listOf(PickerOption(null, stringResource(Res.string.stock_filter_tag_any))) +
                TagFilter.entries.map { PickerOption(it.name, tagLabel(it)) },
            selectedId = filters.tag?.name,
            onSelect = { id -> onFiltersChange(filters.copy(tag = TagFilter.entries.firstOrNull { it.name == id })) },
            onDismiss = dismiss,
        )
        StockPicker.Attribute -> PickerSheet(
            title = stringResource(Res.string.stock_filter_attribute),
            options = listOf(PickerOption(null, stringResource(Res.string.stock_filter_any_attribute))) +
                state.attributeDefinitions.map { PickerOption(it.id, it.label) },
            selectedId = filters.attributeId,
            onSelect = { onFiltersChange(filters.copy(attributeId = it)) },
            onDismiss = dismiss,
        )
        null -> Unit
    }
}

@Composable
private fun trackingLabel(tracking: Tracking): String = stringResource(
    when (tracking) {
        Tracking.QUANTITY -> Res.string.stock_filter_consumables
        Tracking.SERIAL -> Res.string.stock_filter_tools
    },
)

@Composable
private fun tagLabel(tag: TagFilter): String = stringResource(
    when (tag) {
        TagFilter.TAGGED -> Res.string.stock_filter_tag_with
        TagFilter.UNTAGGED -> Res.string.stock_filter_tag_without
    },
)

@Composable
private fun stockLevelLabel(level: StockLevelFilter): String = stringResource(
    when (level) {
        StockLevelFilter.LOW -> Res.string.stock_filter_low_stock
        StockLevelFilter.OUT -> Res.string.stock_filter_out_of_stock
    },
)

@Composable
private fun attributeLabel(definition: AttributeDefinition): String =
    stringResource(Res.string.stock_filter_has_attribute, definition.label)

/** The active filters as short labels, in chip order, for the empty state. */
@Composable
private fun activeFilterLabels(state: StockUiState): List<String> {
    val filters = state.filters
    return listOfNotNull(
        state.selectedLocation?.name,
        state.selectedCategory?.name,
        filters.tracking?.let { trackingLabel(it) },
        filters.stockLevel?.let { stockLevelLabel(it) },
        filters.tag?.let { tagLabel(it) },
        state.selectedAttribute?.let { attributeLabel(it) },
    )
}

@Composable
private fun FilterRow(
    state: StockUiState,
    onOpenPicker: (StockPicker) -> Unit,
    onFiltersChange: (StockFilters) -> Unit,
    onClearFilters: () -> Unit,
) {
    val filters = state.filters
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    ) {
        // First, so it stays visible however many filters are set.
        if (filters.isActive) {
            AssistChip(
                onClick = onClearFilters,
                label = { Text(stringResource(Res.string.stock_filter_clear_all)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_close), null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
        if (state.locations.isNotEmpty()) {
            PickerChip(
                label = stringResource(Res.string.stock_filter_location),
                selectedName = state.selectedLocation?.name,
                leading = state.selectedLocation?.let { { LocationIcon(it.icon, it.color, size = FilterChipDefaults.IconSize) } },
                clearDescription = stringResource(Res.string.stock_filter_clear_location),
                onClick = { onOpenPicker(StockPicker.Location) },
                onClear = { onFiltersChange(filters.copy(locationId = null)) },
            )
        }
        if (state.categories.isNotEmpty()) {
            PickerChip(
                label = stringResource(Res.string.stock_filter_category),
                selectedName = state.selectedCategory?.name,
                clearDescription = stringResource(Res.string.stock_filter_clear_category),
                onClick = { onOpenPicker(StockPicker.Category) },
                onClear = { onFiltersChange(filters.copy(categoryId = null)) },
            )
        }
        PickerChip(
            label = stringResource(Res.string.stock_filter_type),
            selectedName = filters.tracking?.let { trackingLabel(it) },
            clearDescription = stringResource(Res.string.stock_filter_clear_type),
            onClick = { onOpenPicker(StockPicker.Type) },
            onClear = { onFiltersChange(filters.withTracking(null)) },
        )
        StockLevelFilter.entries.forEach { level ->
            val selected = filters.stockLevel == level
            FilterChip(
                selected = selected,
                onClick = { onFiltersChange(filters.toggleStockLevel(level)) },
                label = { Text(stockLevelLabel(level)) },
                // Tools have no low-stock alert: the combination would always be empty.
                enabled = !(level == StockLevelFilter.LOW && filters.tracking == Tracking.SERIAL),
                leadingIcon = if (selected) {
                    { Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
        PickerChip(
            label = stringResource(Res.string.stock_filter_tag),
            selectedName = filters.tag?.let { tagLabel(it) },
            leading = { Icon(painterResource(Res.drawable.ic_nfc), null, Modifier.size(FilterChipDefaults.IconSize)) },
            clearDescription = stringResource(Res.string.stock_filter_clear_tag),
            onClick = { onOpenPicker(StockPicker.Tag) },
            onClear = { onFiltersChange(filters.copy(tag = null)) },
        )
        if (state.attributeDefinitions.isNotEmpty()) {
            PickerChip(
                label = stringResource(Res.string.stock_filter_attribute),
                selectedName = state.selectedAttribute?.let { attributeLabel(it) },
                clearDescription = stringResource(Res.string.stock_filter_clear_attribute),
                onClick = { onOpenPicker(StockPicker.Attribute) },
                onClear = { onFiltersChange(filters.copy(attributeId = null)) },
            )
        }
    }
}

/** Chip that opens a picker. When a value is selected, it shows its name and a clear icon. */
@Composable
private fun PickerChip(
    label: String,
    selectedName: String?,
    clearDescription: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selectedName != null,
        onClick = onClick,
        leadingIcon = leading,
        label = {
            Text(
                selectedName ?: label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        },
        trailingIcon = {
            if (selectedName != null) {
                Icon(
                    painterResource(Res.drawable.ic_cancel),
                    contentDescription = clearDescription,
                    modifier = Modifier
                        .size(FilterChipDefaults.IconSize)
                        .clickable(role = Role.Button, onClick = onClear),
                )
            } else {
                Icon(painterResource(Res.drawable.ic_arrow_drop_down), null, Modifier.size(FilterChipDefaults.IconSize))
            }
        },
    )
}

/** Bottom sheet with a scrollable list of options, indented by depth. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    options: List<PickerOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.id ?: "" }) { option ->
                val selected = option.id == selectedId
                ListItem(
                    leadingContent = if (option.showIcon) {
                        { LocationIcon(option.icon, option.color) }
                    } else {
                        null
                    },
                    headlineContent = {
                        Text(
                            option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = (option.depth * 24).dp),
                        )
                    },
                    trailingContent = if (selected) {
                        { Icon(painterResource(Res.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                        onSelect(option.id)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                )
            }
        }
    }
}

@Composable
private fun StockRow(item: ArticleStock, attributeLine: String?) {
    ListItem(
        leadingContent = { ArticleThumbnail(item.article.imagePath) },
        headlineContent = { Text(item.article.name) },
        supportingContent = if (attributeLine == null && item.article.sku == null) null else {
            {
                Column {
                    if (attributeLine != null) Text(attributeLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.article.sku?.let { sku -> Text(sku, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
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
internal fun LowStockBadge() {
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
private fun EmptyMessage(title: String, hint: String?, action: (@Composable () -> Unit)? = null) {
    // Scrollable so that pull to refresh also starts on the empty state
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
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
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

private fun sample(
    id: String,
    name: String,
    sku: String,
    stockMilli: Long,
    thresholdMilli: Long? = null,
    unit: String = "pce",
    categoryId: String? = null,
) = ArticleStock(
    Article(
        id = id, sku = sku, name = name, unit = unit, thresholdMilli = thresholdMilli,
        categoryId = categoryId, createdAt = 0, updatedAt = 0,
    ),
    stockMilli,
)

private val sampleItems = listOf(
    sample("1", "Bolt M8", "BLT-8", 120_000, thresholdMilli = 50_000, categoryId = "hw"),
    sample("2", "Cable tie 200 mm", "CT-200", 8_000, thresholdMilli = 20_000),
    sample("3", "Duct tape", "TAPE-50", 2_500, unit = "m"),
    sample("4", "Wood screw 4x40", "SCR-440", 0, thresholdMilli = 0, categoryId = "hw"),
)

private val sampleLocations = listOf(
    Location(id = "room", name = "Workshop", createdAt = 0, updatedAt = 0),
    Location(id = "shelf", name = "Shelf A", parentId = "room", createdAt = 0, updatedAt = 0),
    Location(id = "bin", name = "Bin 3", parentId = "shelf", createdAt = 0, updatedAt = 0),
).toLocationTree()

private val sampleCategories = listOf(Category(id = "hw", name = "Hardware", createdAt = 0, updatedAt = 0))

private fun sampleState(
    items: List<ArticleStock> = sampleItems,
    filters: StockFilters = StockFilters(),
) = StockUiState(
    loading = false,
    items = items,
    totalCount = sampleItems.size,
    locations = sampleLocations,
    categories = sampleCategories,
    filters = filters,
)

@Composable
private fun StockPreview(state: StockUiState, query: String = "", dark: Boolean = isSystemInDarkTheme()) {
    EasyStockTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            StockContent(
                state = state,
                query = query,
                onQueryChange = {},
                onFiltersChange = {},
                onClearFilters = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StockPagePreview() {
    StockPreview(sampleState(), dark = false)
}

@Preview(showBackground = true)
@Composable
private fun StockPageDarkPreview() {
    StockPreview(sampleState(), dark = true)
}

@Preview(showBackground = true)
@Composable
private fun StockPageFiltersPreview() {
    StockPreview(
        sampleState(
            items = listOf(sample("4", "Wood screw 4x40", "SCR-440", 0, thresholdMilli = 0, categoryId = "hw")),
            filters = StockFilters(locationId = "shelf", categoryId = "hw", stockLevel = StockLevelFilter.LOW),
        ),
        dark = false,
    )
}

@Preview(showBackground = true)
@Composable
private fun StockPageFiltersDarkPreview() {
    StockPreview(
        sampleState(
            items = listOf(sample("4", "Wood screw 4x40", "SCR-440", 0, thresholdMilli = 0, categoryId = "hw")),
            filters = StockFilters(locationId = "shelf", tracking = Tracking.QUANTITY, tag = TagFilter.UNTAGGED, stockLevel = StockLevelFilter.LOW),
        ),
        dark = true,
    )
}

@Preview(showBackground = true)
@Composable
private fun StockPageEmptyPreview() {
    StockPreview(StockUiState(loading = false), dark = false)
}

@Preview(showBackground = true)
@Composable
private fun StockPageNoResultsPreview() {
    StockPreview(sampleState(items = emptyList()), query = "nail", dark = false)
}

@Preview(showBackground = true)
@Composable
private fun StockPageFiltersNoResultsPreview() {
    StockPreview(sampleState(items = emptyList(), filters = StockFilters(categoryId = "hw", stockLevel = StockLevelFilter.LOW)), dark = false)
}
