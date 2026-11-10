package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import ch.mathorck.easystock.composables.LocationIcon
import ch.mathorck.easystock.composables.locationColor
import ch.mathorck.easystock.composables.locationColorLabel
import ch.mathorck.easystock.composables.locationIconLabel
import ch.mathorck.easystock.composables.locationIconRes
import ch.mathorck.easystock.data.model.LocationColors
import ch.mathorck.easystock.data.model.LocationIcons
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.locations_color
import easystock.shared.generated.resources.locations_color_default
import easystock.shared.generated.resources.locations_icon
import easystock.shared.generated.resources.locations_icon_default
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.viewmodels.LocationDialogState
import ch.mathorck.easystock.viewmodels.LocationError
import ch.mathorck.easystock.viewmodels.LocationForm
import ch.mathorck.easystock.viewmodels.LocationListUiState
import ch.mathorck.easystock.viewmodels.LocationListViewModel
import ch.mathorck.easystock.viewmodels.LocationMessage
import ch.mathorck.easystock.viewmodels.LocationRow
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.attributes_cancel
import easystock.shared.generated.resources.attributes_delete
import easystock.shared.generated.resources.attributes_error_too_long
import easystock.shared.generated.resources.attributes_save
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_delete
import easystock.shared.generated.resources.locations_add
import easystock.shared.generated.resources.locations_add_sub
import easystock.shared.generated.resources.locations_blocked_stock
import easystock.shared.generated.resources.locations_delete_children_message
import easystock.shared.generated.resources.locations_delete_failed
import easystock.shared.generated.resources.locations_delete_message
import easystock.shared.generated.resources.locations_delete_title
import easystock.shared.generated.resources.locations_deleted
import easystock.shared.generated.resources.locations_edit_title
import easystock.shared.generated.resources.locations_empty
import easystock.shared.generated.resources.locations_error_duplicate
import easystock.shared.generated.resources.locations_error_save
import easystock.shared.generated.resources.locations_name
import easystock.shared.generated.resources.locations_new_title
import easystock.shared.generated.resources.locations_parent
import easystock.shared.generated.resources.locations_parent_none
import easystock.shared.generated.resources.locations_stock_articles
import easystock.shared.generated.resources.locations_stock_none
import easystock.shared.generated.resources.locations_title
import easystock.shared.generated.resources.ic_fact_check
import easystock.shared.generated.resources.review_last_reviewed
import easystock.shared.generated.resources.review_location_action
import ch.mathorck.easystock.composables.review.ReviewScreen
import ch.mathorck.easystock.composables.review.rememberReviewLauncher
import ch.mathorck.easystock.viewmodels.formatDateTime
import ch.mathorck.easystock.viewmodels.lastReviewedByLocation
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Settings > Locations. [onBack] runs on the arrow and the system back. */
@Composable
fun LocationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val db = rememberAppDatabase()
    val viewModel = viewModel(key = "settings-locations") { LocationListViewModel(db.locationDao()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reviewDao = db.reviewDao()
    val reviewed by remember(reviewDao) { reviewDao.observeLastReviewed() }.collectAsStateWithLifecycle(emptyList())
    val lastReviewed = remember(reviewed, state.rows) {
        lastReviewedByLocation(state.rows.map { it.location }, reviewed.associate { it.locationId to it.finishedAt })
    }
    val review = rememberReviewLauncher(reviewDao)
    val openReview = review.open
    if (openReview != null) {
        ReviewScreen(reviewId = openReview, onBack = review.close, modifier = modifier)
        return
    }
    val back = {
        viewModel.cancel()
        onBack()
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = back)

    LocationsContent(
        state = state,
        onBack = back,
        onAdd = { viewModel.startCreate() },
        onAddSub = viewModel::startCreate,
        onEdit = viewModel::startEdit,
        onDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancel = viewModel::cancel,
        onMessageShown = viewModel::consumeMessage,
        modifier = modifier,
        lastReviewed = lastReviewed,
        onReview = { review.start(it.id) },
    )
    state.dialog?.let { dialog ->
        LocationDialog(
            state = dialog,
            onFormChange = viewModel::updateForm,
            onDismiss = viewModel::cancel,
            onSave = viewModel::save,
        )
    }
}

@Composable
fun LocationsContent(
    state: LocationListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onAddSub: (Location) -> Unit,
    onEdit: (Location) -> Unit,
    onDelete: (Location) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit,
    onMessageShown: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Last finished review covering each location (its own or an ancestor's), epoch millis UTC. */
    lastReviewed: Map<String, Long> = emptyMap(),
    onReview: ((Location) -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { it.text() }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }
    Box(modifier) {
        SettingsSubpage(
            title = stringResource(Res.string.locations_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = onAdd) {
                    Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.locations_add))
                }
            },
        ) {
            if (!state.loading && state.rows.isEmpty()) {
                EmptyListHint(stringResource(Res.string.locations_empty))
            }
            LazyColumn {
                items(state.rows, key = { it.location.id }) { row ->
                    LocationRowItem(
                        row,
                        lastReviewed = lastReviewed[row.location.id],
                        onEdit = onEdit,
                        onAddSub = onAddSub,
                        onDelete = onDelete,
                        onReview = onReview,
                    )
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
    state.pendingDelete?.let { request ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.locations_delete_title),
            message = if (request.descendantCount > 0) {
                pluralStringResource(
                    Res.plurals.locations_delete_children_message,
                    request.descendantCount,
                    request.descendantCount,
                    request.location.name,
                )
            } else {
                stringResource(Res.string.locations_delete_message, request.location.name)
            },
            onConfirm = onConfirmDelete,
            onDismiss = onCancel,
        )
    }
}

@Composable
private fun LocationMessage.text(): String = when (this) {
    is LocationMessage.HasStock -> stringResource(Res.string.locations_blocked_stock, name)
    is LocationMessage.Deleted -> pluralStringResource(Res.plurals.locations_deleted, count, count)
    LocationMessage.DeleteFailed -> stringResource(Res.string.locations_delete_failed)
}

@Composable
private fun LocationRowItem(
    row: LocationRow,
    lastReviewed: Long?,
    onEdit: (Location) -> Unit,
    onAddSub: (Location) -> Unit,
    onDelete: (Location) -> Unit,
    onReview: ((Location) -> Unit)?,
) {
    val location = row.location
    val timeZone = remember { TimeZone.currentSystemDefault() }
    ListItem(
        headlineContent = { Text(location.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val stock = if (row.articleCount == 0) {
                stringResource(Res.string.locations_stock_none)
            } else {
                pluralStringResource(Res.plurals.locations_stock_articles, row.articleCount, row.articleCount)
            }
            val reviewed = lastReviewed?.let { "\n" + stringResource(Res.string.review_last_reviewed, formatDateTime(it, timeZone)) } ?: ""
            Text(stock + reviewed)
        },
        leadingContent = { LocationIcon(location.icon, location.color) },
        trailingContent = {
            Row {
                if (onReview != null) {
                    IconButton(onClick = { onReview(location) }) {
                        Icon(painterResource(Res.drawable.ic_fact_check), contentDescription = stringResource(Res.string.review_location_action))
                    }
                }
                IconButton(onClick = { onAddSub(location) }) {
                    Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.locations_add_sub))
                }
                IconButton(onClick = { onDelete(location) }) {
                    Icon(painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.attributes_delete))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable { onEdit(location) }
            .padding(start = (row.depth.coerceAtMost(8) * 20).dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDialog(
    state: LocationDialogState,
    onFormChange: ((LocationForm) -> LocationForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val form = state.form
    val isNew = state.editing == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) Res.string.locations_new_title else Res.string.locations_edit_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                val nameError = when (state.error) {
                    LocationError.TOO_LONG -> stringResource(Res.string.attributes_error_too_long)
                    LocationError.DUPLICATE -> stringResource(Res.string.locations_error_duplicate)
                    else -> null
                }
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> onFormChange { it.copy(name = value) } },
                    label = { Text(stringResource(Res.string.locations_name)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ParentDropdown(state, onSelected = { parentId -> onFormChange { it.copy(parentId = parentId) } })
                AppearancePicker(
                    form = form,
                    onIcon = { key -> onFormChange { it.copy(icon = key) } },
                    onColor = { key -> onFormChange { it.copy(color = key) } },
                )
                if (state.saveFailed) {
                    Text(
                        stringResource(Res.string.locations_error_save),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.attributes_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.attributes_cancel)) }
        },
    )
}

/** Parent picker: top level, then the tree indented. The location itself and its descendants are not offered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentDropdown(state: LocationDialogState, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(Res.string.locations_parent_none)
    val selected = state.parents.firstOrNull { it.location.id == state.form.parentId }?.location?.name ?: none
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.locations_parent)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(none) },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            state.parents.forEach { node ->
                DropdownMenuItem(
                    text = { Text(node.location.name, modifier = Modifier.padding(start = (node.depth.coerceAtMost(8) * 16).dp)) },
                    onClick = {
                        expanded = false
                        onSelected(node.location.id)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Preview, icon grid and color swatches. Null = default icon / color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePicker(form: LocationForm, onIcon: (String?) -> Unit, onColor: (String?) -> Unit) {
    val tint = locationColor(form.color) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        ) {
            LocationIcon(form.icon, form.color, size = 28.dp)
        }
        Text(
            form.name.trim().ifEmpty { stringResource(Res.string.locations_name) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(stringResource(Res.string.locations_icon), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The first cell is the default icon (key null)
        (listOf<String?>(null) + LocationIcons.keys).forEach { key ->
            val selected = LocationIcons.normalize(form.icon) == key
            val label = stringResource(locationIconLabel(key))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) tint.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        if (selected) BorderStroke(2.dp, tint) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(role = Role.RadioButton) { onIcon(key) }
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
            ) {
                Icon(
                    painterResource(locationIconRes(key)),
                    contentDescription = null,
                    tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    Text(stringResource(Res.string.locations_color), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<String?>(null) + LocationColors.keys).forEach { key ->
            val selected = LocationColors.normalize(form.color) == key
            val label = stringResource(locationColorLabel(key))
            val swatch = locationColor(key)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(swatch ?: Color.Transparent)
                    .border(
                        if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        CircleShape,
                    )
                    .clickable(role = Role.RadioButton) { onColor(key) }
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
            ) {
                // Default swatch: an empty circle with a slash-free check when chosen
                if (swatch == null && selected) {
                    Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                } else if (swatch == null) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
    }
}
