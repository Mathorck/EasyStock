package ch.mathorck.easystock.composables.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.LocationIcon
import ch.mathorck.easystock.composables.article.ArticleDetailScreen
import ch.mathorck.easystock.composables.article.StockActionSheet
import ch.mathorck.easystock.composables.attributes.summaryLine
import ch.mathorck.easystock.composables.review.ReviewScreen
import ch.mathorck.easystock.composables.review.rememberReviewLauncher
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.composables.settings.SettingsSubpage
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.ContentsArticleRow
import ch.mathorck.easystock.viewmodels.ContentsSubLocation
import ch.mathorck.easystock.viewmodels.LocationContentsUiState
import ch.mathorck.easystock.viewmodels.LocationContentsViewModel
import ch.mathorck.easystock.viewmodels.StockActionForm
import ch.mathorck.easystock.viewmodels.StockActionKind
import ch.mathorck.easystock.viewmodels.StockActionViewModel
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_chevron_right
import easystock.shared.generated.resources.ic_fact_check
import easystock.shared.generated.resources.ic_nfc
import easystock.shared.generated.resources.ic_remove
import easystock.shared.generated.resources.ic_swap_horiz
import easystock.shared.generated.resources.location_contents_articles
import easystock.shared.generated.resources.location_contents_empty
import easystock.shared.generated.resources.location_contents_empty_here
import easystock.shared.generated.resources.location_contents_gone
import easystock.shared.generated.resources.location_contents_here
import easystock.shared.generated.resources.location_contents_include_sub
import easystock.shared.generated.resources.location_contents_review
import easystock.shared.generated.resources.location_contents_sub_locations
import easystock.shared.generated.resources.location_contents_units
import easystock.shared.generated.resources.locations_has_tag
import easystock.shared.generated.resources.stock_action_add
import easystock.shared.generated.resources.stock_action_move
import easystock.shared.generated.resources.stock_action_no_serial
import easystock.shared.generated.resources.stock_action_remove
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A stock action started from the contents: the sheet of [article], opened with [form]. */
private data class ContentsAction(val article: Article, val form: StockActionForm)

/**
 * What [locationId] holds (e.g. after scanning its tag): sub-locations, articles with their stock,
 * attributes and units, quick stock actions, and a review of the location.
 * [onBack] runs when going back from the first location opened.
 */
@Composable
fun LocationContentsScreen(locationId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val viewModel = viewModel(key = "location-contents-$locationId") {
        LocationContentsViewModel(locationId, db.locationDao(), db.attributeDao())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review = rememberReviewLauncher(db.reviewDao())
    var openArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<ContentsAction?>(null) }

    review.open?.let {
        ReviewScreen(reviewId = it, onBack = review.close, modifier = modifier)
        return
    }
    openArticleId?.let {
        ArticleDetailScreen(articleId = it, onBack = { openArticleId = null }, modifier = modifier)
        return
    }
    val back = { if (!viewModel.back()) onBack() }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = back)

    LocationContentsContent(
        state = state,
        onBack = back,
        onIncludeSubLocations = viewModel::setIncludeSubLocations,
        onOpenLocation = viewModel::open,
        onOpenArticle = { openArticleId = it.id },
        onAction = { article, form -> action = ContentsAction(article, form) },
        onReview = { state.location?.let { review.start(it.id) } },
        modifier = modifier,
    )
    action?.let { StockActionHost(it, onClose = { action = null }) }
}

/** Opens the stock action sheet of the article; [onClose] when it closes (saved or dismissed). */
@Composable
private fun StockActionHost(request: ContentsAction, onClose: () -> Unit) {
    val db = rememberAppDatabase()
    val articleId = request.article.id
    // Same key as the article detail: one instance per article
    val actions = viewModel(key = "stock-actions-$articleId") {
        StockActionViewModel(articleId, db.stockActionDao(), db.articleDetailDao(), db.stockDao(), db.attributeDao())
    }
    val sheet by actions.sheet.collectAsStateWithLifecycle()
    var opened by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request) { actions.open(request.form) }
    LaunchedEffect(sheet == null) {
        if (sheet != null) opened = true else if (opened) onClose()
    }
    sheet?.let {
        StockActionSheet(
            state = it,
            unit = request.article.unit,
            onFormChange = actions::update,
            onDismiss = actions::close,
            onSave = actions::save,
        )
    }
}

@Composable
fun LocationContentsContent(
    state: LocationContentsUiState,
    onBack: () -> Unit,
    onIncludeSubLocations: (Boolean) -> Unit,
    onOpenLocation: (String) -> Unit,
    onOpenArticle: (Article) -> Unit,
    onAction: (Article, StockActionForm) -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val location = state.location
    SettingsSubpage(
        title = location?.name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (location != null) {
                IconButton(onClick = onReview) {
                    Icon(painterResource(Res.drawable.ic_fact_check), contentDescription = stringResource(Res.string.location_contents_review))
                }
            }
        },
    ) {
        when {
            state.loading -> Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                CircularProgressIndicator()
            }
            location == null -> EmptyListHint(stringResource(Res.string.location_contents_gone))
            else -> LazyColumn {
                item(key = "header") {
                    Header(state, onIncludeSubLocations)
                }
                if (state.subLocations.isNotEmpty()) {
                    item(key = "sub-title") { SectionTitle(stringResource(Res.string.location_contents_sub_locations)) }
                    items(state.subLocations, key = { "sub-${it.location.id}" }) { sub ->
                        SubLocationRow(sub, onClick = { onOpenLocation(sub.location.id) })
                    }
                    item(key = "sub-divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                if (state.isEmpty) {
                    item(key = "empty") {
                        val hiddenBelow = !state.includeSubLocations && state.subLocations.any { it.articleCount > 0 }
                        EmptyListHint(
                            stringResource(if (hiddenBelow) Res.string.location_contents_empty_here else Res.string.location_contents_empty),
                        )
                    }
                }
                state.groups.forEach { group ->
                    if (state.includeSubLocations) {
                        item(key = "group-${group.location.id}") {
                            SectionTitle(group.path.ifEmpty { stringResource(Res.string.location_contents_here) })
                        }
                    }
                    items(group.rows, key = { "row-${it.locationId}-${it.article.id}" }) { row ->
                        ArticleRow(
                            row = row,
                            onOpen = { onOpenArticle(row.article) },
                            onAction = { form -> onAction(row.article, form) },
                        )
                    }
                }
                item(key = "review") {
                    Button(onClick = onReview, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Icon(painterResource(Res.drawable.ic_fact_check), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(Res.string.location_contents_review))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: LocationContentsUiState, onIncludeSubLocations: (Boolean) -> Unit) {
    val location = state.location ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LocationIcon(location.icon, location.color, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(state.path, style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(Res.plurals.location_contents_articles, state.articleCount, state.articleCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (location.barcode != null) {
                Icon(
                    painterResource(Res.drawable.ic_nfc),
                    contentDescription = stringResource(Res.string.locations_has_tag),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(
                value = state.includeSubLocations,
                role = Role.Switch,
                onValueChange = onIncludeSubLocations,
            ).padding(vertical = 4.dp),
        ) {
            Text(stringResource(Res.string.location_contents_include_sub), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.includeSubLocations, onCheckedChange = null)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubLocationRow(sub: ContentsSubLocation, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(sub.location.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(pluralStringResource(Res.plurals.location_contents_articles, sub.articleCount, sub.articleCount)) },
        leadingContent = { LocationIcon(sub.location.icon, sub.location.color) },
        trailingContent = { Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** An article at one location: tap opens it; move, remove and add act on the stock there. */
@Composable
private fun ArticleRow(row: ContentsArticleRow, onOpen: () -> Unit, onAction: (StockActionForm) -> Unit) {
    val article = row.article
    Column {
        ListItem(
            headlineContent = { Text(article.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    Text("${formatQuantity(row.stockMilli)} ${article.unit}", style = MaterialTheme.typography.titleSmall)
                    val attributes = row.attributes.summaryLine()
                    if (attributes.isNotEmpty()) Text(attributes, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                Row {
                    if (row.isSerial) {
                        RoundAction(Res.drawable.ic_add, Res.string.stock_action_add) {
                            onAction(StockActionForm(kind = StockActionKind.ADD_UNIT, targetLocationId = row.locationId))
                        }
                    } else {
                        IconButton(onClick = { onAction(StockActionForm(kind = StockActionKind.MOVE, sourceLocationId = row.locationId)) }) {
                            Icon(painterResource(Res.drawable.ic_swap_horiz), contentDescription = stringResource(Res.string.stock_action_move))
                        }
                        RoundAction(Res.drawable.ic_remove, Res.string.stock_action_remove) {
                            onAction(StockActionForm(kind = StockActionKind.REMOVE, sourceLocationId = row.locationId))
                        }
                        RoundAction(Res.drawable.ic_add, Res.string.stock_action_add) {
                            onAction(StockActionForm(kind = StockActionKind.ADD, sourceLocationId = row.locationId))
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable(onClick = onOpen),
        )
        if (row.units.isNotEmpty()) {
            Text(
                stringResource(Res.string.location_contents_units),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 16.dp),
            )
            row.units.forEach { unit ->
                UnitLine(unit, onMove = { onAction(StockActionForm(kind = StockActionKind.MOVE_UNIT, itemId = unit.id)) })
            }
        }
    }
}

@Composable
private fun UnitLine(unit: Item, onMove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 8.dp),
    ) {
        Text(
            unit.serialNumber ?: stringResource(Res.string.stock_action_no_serial),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMove) {
            Icon(painterResource(Res.drawable.ic_swap_horiz), contentDescription = stringResource(Res.string.stock_action_move))
        }
    }
}

@Composable
private fun RoundAction(icon: DrawableResource, label: StringResource, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp).size(40.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}
