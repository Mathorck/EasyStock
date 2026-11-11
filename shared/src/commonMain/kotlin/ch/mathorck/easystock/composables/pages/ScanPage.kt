package ch.mathorck.easystock.composables.pages

import ch.mathorck.easystock.theme.EasyStockTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import ch.mathorck.easystock.enums.ScanState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mathorck.easystock.composables.article.ArticleDetailScreen
import ch.mathorck.easystock.composables.article.ArticleFormDialog
import ch.mathorck.easystock.composables.nfc.TagMoveDialog
import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.composables.nfc.TagPickerSheet
import kotlinx.coroutines.flow.combine
import ch.mathorck.easystock.data.db.asTagStore
import ch.mathorck.easystock.composables.location.LocationContentsScreen
import ch.mathorck.easystock.composables.settings.LocationDialog
import ch.mathorck.easystock.viewmodels.LocationListViewModel
import ch.mathorck.easystock.viewmodels.TagPickerType
import easystock.shared.generated.resources.nfc_create_location_with_tag
import easystock.shared.generated.resources.nfc_link_existing_location
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.images.rememberArticleImageStore
import ch.mathorck.easystock.nfc.NfcTagListener
import ch.mathorck.easystock.nfc.TagPriority
import ch.mathorck.easystock.nfc.appNfcTags
import ch.mathorck.easystock.nfc.rememberOpenNfcSettings
import ch.mathorck.easystock.viewmodels.NewArticleViewModel
import ch.mathorck.easystock.viewmodels.ScanViewModel
import easystock.shared.generated.resources.nfc_cancel
import easystock.shared.generated.resources.nfc_create_with_tag
import easystock.shared.generated.resources.nfc_link_existing
import easystock.shared.generated.resources.nfc_open_settings
import easystock.shared.generated.resources.nfc_tag_id
import easystock.shared.generated.resources.nfc_try_again
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_scan
import easystock.shared.generated.resources.ic_scan_off
import easystock.shared.generated.resources.scan_hint_error
import easystock.shared.generated.resources.scan_hint_nfc_disabled
import easystock.shared.generated.resources.scan_hint_nfc_unavailable
import easystock.shared.generated.resources.scan_hint_not_found
import easystock.shared.generated.resources.scan_hint_reading
import easystock.shared.generated.resources.scan_hint_ready
import easystock.shared.generated.resources.scan_hint_success
import easystock.shared.generated.resources.scan_state_error
import easystock.shared.generated.resources.scan_state_nfc_disabled
import easystock.shared.generated.resources.scan_state_nfc_unavailable
import easystock.shared.generated.resources.scan_state_not_found
import easystock.shared.generated.resources.scan_state_reading
import easystock.shared.generated.resources.scan_state_ready
import easystock.shared.generated.resources.scan_state_success
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Waits for an NFC tag. A known tag opens its article or shows what its location holds; an unknown one
 * can be linked to an article or location without tag, or used for a new article or location.
 */
@Composable
fun ScanPage() {
    val db = rememberAppDatabase()
    val viewModel = viewModel {
        val attributes = db.attributeDao()
        ScanViewModel(
            db.tagDao().asTagStore(),
            appNfcTags.availability,
            catalog = combine(attributes.observeDefinitions(), attributes.observeActiveLinks(), ::AttributeCatalog),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val images = rememberArticleImageStore()
    // Keyed: not the instance of the Stock page
    val newArticleViewModel = viewModel(key = "scan-new-article") {
        NewArticleViewModel(db.articleFormDao(), db.categoryDao(), db.attributeDao(), images)
    }
    val newArticleState by newArticleViewModel.state.collectAsStateWithLifecycle()
    val createdArticleId by newArticleViewModel.created.collectAsStateWithLifecycle()
    // The article created with the tag opens its detail
    LaunchedEffect(createdArticleId) {
        createdArticleId?.let {
            viewModel.openArticle(it)
            newArticleViewModel.consumeCreated()
        }
    }
    // Keyed: not the instance of Settings > Locations
    val newLocationViewModel = viewModel(key = "scan-new-location") { LocationListViewModel(db.locationDao()) }
    val newLocationState by newLocationViewModel.state.collectAsStateWithLifecycle()
    val createdLocationId by newLocationViewModel.created.collectAsStateWithLifecycle()
    // The location created with the tag shows its contents
    LaunchedEffect(createdLocationId) {
        createdLocationId?.let {
            viewModel.openLocation(it)
            newLocationViewModel.consumeCreated()
        }
    }
    val locationDialog = newLocationState.dialog
    NfcTagListener(
        TagPriority.Lookup,
        enabled = state.acceptsTags && newArticleState == null && locationDialog == null,
        onEvent = viewModel::onTag,
    )

    val openArticleId = state.openArticleId
    val openLocationId = state.openLocationId
    when {
        openArticleId != null -> ArticleDetailScreen(articleId = openArticleId, onBack = viewModel::reset)
        openLocationId != null -> LocationContentsScreen(locationId = openLocationId, onBack = viewModel::reset)
        else -> ScanContent(
            state = state.scanState,
            tagId = state.unknownTagId,
            onOpenSettings = rememberOpenNfcSettings(),
            onTryAgain = viewModel::reset,
            onLinkExisting = { viewModel.openPicker(TagPickerType.ARTICLE) },
            onCreateWithTag = { state.unknownTagId?.let(newArticleViewModel::start) },
            onLinkExistingLocation = { viewModel.openPicker(TagPickerType.LOCATION) },
            onCreateLocationWithTag = { state.unknownTagId?.let { newLocationViewModel.startCreate(tagId = it) } },
            onCancel = viewModel::reset,
        )
    }
    locationDialog?.let { dialog ->
        LocationDialog(
            state = dialog,
            onFormChange = newLocationViewModel::updateForm,
            onDismiss = newLocationViewModel::cancel,
            onSave = newLocationViewModel::save,
        )
    }
    state.picker?.let { picker ->
        TagPickerSheet(
            state = picker,
            onQueryChange = viewModel::onPickerQuery,
            onPick = viewModel::linkTo,
            onDismiss = viewModel::closePicker,
        )
    }
    state.moveRequest?.let { request ->
        TagMoveDialog(request, request.targetName, onConfirm = viewModel::confirmMove, onDismiss = viewModel::cancelMove)
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

/**
 * The scan circle and the actions of [state]. [tagId] is the unknown tag while [ScanState.NotFound].
 */
@Composable
fun ScanContent(
    state: ScanState,
    tagId: String? = null,
    onOpenSettings: () -> Unit = {},
    onTryAgain: () -> Unit = {},
    onLinkExisting: () -> Unit = {},
    onCreateWithTag: () -> Unit = {},
    onLinkExistingLocation: () -> Unit = {},
    onCreateLocationWithTag: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val withActions = state == ScanState.NotFound || state == ScanState.Error || state == ScanState.NfcDisabled
        // Smaller circle when buttons are shown under it
        val circleSize = min(min(maxWidth, maxHeight * if (withActions) 0.5f else 0.75f), 460.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            ScanCircle(state = state, modifier = Modifier.size(circleSize))
            // More detail about the state, under the circle
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { target ->
                Text(
                    text = stringResource(target.hint()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
            when (state) {
                ScanState.NfcDisabled -> Button(onClick = onOpenSettings) { Text(stringResource(Res.string.nfc_open_settings)) }
                ScanState.Error -> Button(onClick = onTryAgain) { Text(stringResource(Res.string.nfc_try_again)) }
                ScanState.NotFound -> {
                    tagId?.let {
                        Text(
                            stringResource(Res.string.nfc_tag_id, it),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onLinkExisting, modifier = Modifier.widthIn(min = 280.dp)) {
                        Text(stringResource(Res.string.nfc_link_existing))
                    }
                    OutlinedButton(onClick = onCreateWithTag, modifier = Modifier.widthIn(min = 280.dp)) {
                        Text(stringResource(Res.string.nfc_create_with_tag))
                    }
                    Button(onClick = onLinkExistingLocation, modifier = Modifier.widthIn(min = 280.dp)) {
                        Text(stringResource(Res.string.nfc_link_existing_location))
                    }
                    OutlinedButton(onClick = onCreateLocationWithTag, modifier = Modifier.widthIn(min = 280.dp)) {
                        Text(stringResource(Res.string.nfc_create_location_with_tag))
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(Res.string.nfc_cancel)) }
                }
                else -> Unit
            }
        }
    }
}

private const val ECHO_RINGS = 3
private const val ECHO_GROWTH = 0.3f

@Composable
private fun ScanCircle(state: ScanState, modifier: Modifier = Modifier) {
    val color by animateColorAsState(state.color())
    val waiting = state == ScanState.Ready
    val reading = state == ScanState.Reading

    val transition = rememberInfiniteTransition()
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
    )
    val echo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing)),
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            // Leave room for the echo rings around the circle
            val radius = (size.minDimension - strokeWidth) / 2 / (1 + ECHO_GROWTH)
            drawCircle(color = color.copy(alpha = 0.12f), radius = radius)
            if (waiting) {
                // Echo: rings grow out of the circle and fade
                repeat(ECHO_RINGS) { i ->
                    val progress = (echo + i.toFloat() / ECHO_RINGS) % 1f
                    drawCircle(
                        color = color.copy(alpha = 0.5f * (1 - progress)),
                        radius = radius * (1 + ECHO_GROWTH * progress),
                        style = Stroke(width = strokeWidth * (1 - progress / 2)),
                    )
                }
            }
            if (reading) {
                // Dashed rotating ring while a tag is read
                rotate(rotation) {
                    drawCircle(
                        color = color,
                        radius = radius,
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 10.dp.toPx())),
                        ),
                    )
                }
            } else {
                drawCircle(color = color, radius = radius, style = Stroke(width = strokeWidth))
            }
        }
        Column(
            // Keep the content inside the inner circle
            modifier = Modifier.fillMaxSize(1 / (1 + ECHO_GROWTH)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(state.icon()),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp),
            )
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { target ->
                Text(
                    text = stringResource(target.label()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ScanState.color(): Color = when (this) {
    ScanState.Ready, ScanState.Reading, ScanState.Success -> MaterialTheme.colorScheme.primary
    ScanState.NotFound -> MaterialTheme.colorScheme.secondary
    ScanState.Error -> MaterialTheme.colorScheme.error
    ScanState.NfcDisabled, ScanState.NfcUnavailable -> MaterialTheme.colorScheme.outline
}

private fun ScanState.icon(): DrawableResource = when (this) {
    ScanState.NfcDisabled, ScanState.NfcUnavailable -> Res.drawable.ic_scan_off
    else -> Res.drawable.ic_scan
}

private fun ScanState.hint(): StringResource = when (this) {
    ScanState.Ready -> Res.string.scan_hint_ready
    ScanState.Reading -> Res.string.scan_hint_reading
    ScanState.Success -> Res.string.scan_hint_success
    ScanState.NotFound -> Res.string.scan_hint_not_found
    ScanState.Error -> Res.string.scan_hint_error
    ScanState.NfcDisabled -> Res.string.scan_hint_nfc_disabled
    ScanState.NfcUnavailable -> Res.string.scan_hint_nfc_unavailable
}

private fun ScanState.label(): StringResource = when (this) {
    ScanState.Ready -> Res.string.scan_state_ready
    ScanState.Reading -> Res.string.scan_state_reading
    ScanState.Success -> Res.string.scan_state_success
    ScanState.NotFound -> Res.string.scan_state_not_found
    ScanState.Error -> Res.string.scan_state_error
    ScanState.NfcDisabled -> Res.string.scan_state_nfc_disabled
    ScanState.NfcUnavailable -> Res.string.scan_state_nfc_unavailable
}

@Preview(showBackground = true)
@Composable
private fun ScanPagePreview() {
    EasyStockTheme {
        ScanContent(ScanState.Ready)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanPageReadingPreview() {
    EasyStockTheme {
        ScanContent(ScanState.Reading)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanPageSuccessPreview() {
    EasyStockTheme {
        ScanContent(ScanState.Success)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanPageErrorPreview() {
    EasyStockTheme {
        ScanContent(ScanState.Error)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanPageNfcDisabledPreview() {
    EasyStockTheme {
        ScanContent(ScanState.NfcDisabled)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanPageNotFoundPreview() {
    EasyStockTheme {
        ScanContent(ScanState.NotFound, tagId = "04A2B3C4D5E680")
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanPageDarkPreview() {
    EasyStockTheme(darkTheme = true) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            ScanContent(ScanState.Ready)
        }
    }
}
