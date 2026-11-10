package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.mathorck.easystock.images.CropAspect
import ch.mathorck.easystock.images.CropHandle
import ch.mathorck.easystock.images.CropPreview
import ch.mathorck.easystock.images.CropRect
import ch.mathorck.easystock.images.CropRequest
import ch.mathorck.easystock.images.CropSource
import ch.mathorck.easystock.images.dragCrop
import ch.mathorck.easystock.images.fitViewport
import ch.mathorck.easystock.images.hitTest
import ch.mathorck.easystock.images.initialCrop
import ch.mathorck.easystock.images.minCropSize
import ch.mathorck.easystock.images.rememberArticleImageStore
import ch.mathorck.easystock.images.rotateRect90
import ch.mathorck.easystock.images.toCropBox
import ch.mathorck.easystock.images.withAspect
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.article_image_crop_area
import easystock.shared.generated.resources.article_image_crop_cancel
import easystock.shared.generated.resources.article_image_crop_confirm
import easystock.shared.generated.resources.article_image_crop_free
import easystock.shared.generated.resources.article_image_crop_rotate
import easystock.shared.generated.resources.article_image_crop_saving
import easystock.shared.generated.resources.article_image_crop_square
import easystock.shared.generated.resources.article_image_crop_title
import easystock.shared.generated.resources.ic_rotate_right
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val CropBackground = Color(0xFF101010)
private val CropForeground = Color.White

/**
 * Full-screen crop of [source]. [onSaved] receives the path of the new photo. [onCancel] when the user
 * leaves without saving: the caller then discards the source ([ch.mathorck.easystock.images.ArticleImageStore.discardCrop]).
 * [onError] when the image can't be read or saved.
 */
@Composable
fun CropDialog(
    source: CropSource,
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
    onError: () -> Unit,
) {
    val store = rememberArticleImageStore()
    val scope = rememberCoroutineScope()
    val cancel by rememberUpdatedState(onCancel)
    val saved by rememberUpdatedState(onSaved)
    val failed by rememberUpdatedState(onError)
    // Null while loading; an unreadable image is reported and the screen closed
    val preview: CropPreview? by produceState<CropPreview?>(null, source) {
        value = store.openCrop(source)
        if (value == null) failed()
    }
    var saving by remember { mutableStateOf(false) }
    val loaded = preview

    Dialog(
        onDismissRequest = { if (!saving) cancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CropBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val state = loaded?.let { rememberCropState(it) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(
                    onClick = { cancel() },
                    enabled = !saving,
                    colors = ButtonDefaults.textButtonColors(contentColor = CropForeground),
                ) {
                    Text(stringResource(Res.string.article_image_crop_cancel))
                }
                Text(
                    stringResource(Res.string.article_image_crop_title),
                    color = CropForeground,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Button(
                    enabled = state != null && !saving,
                    onClick = {
                        val s = state ?: return@Button
                        saving = true
                        scope.launch {
                            // Not cancellable: the file is written and reported even if the screen is left meanwhile
                            withContext(NonCancellable) {
                                val path = try {
                                    store.saveCrop(source, s.request())
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                                saving = false
                                if (path != null) saved(path) else failed()
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.article_image_crop_confirm))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (loaded != null && state != null) {
                    CropCanvas(loaded, state, enabled = !saving)
                } else {
                    CircularProgressIndicator(color = CropForeground)
                }
                if (saving) {
                    val savingText = stringResource(Res.string.article_image_crop_saving)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .semantics { contentDescription = savingText },
                    ) {
                        CircularProgressIndicator(color = CropForeground)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val enabled = state != null && !saving
                AspectChip(stringResource(Res.string.article_image_crop_free), state?.aspect == CropAspect.FREE, enabled) {
                    state?.selectAspect(CropAspect.FREE)
                }
                AspectChip(stringResource(Res.string.article_image_crop_square), state?.aspect == CropAspect.SQUARE, enabled) {
                    state?.selectAspect(CropAspect.SQUARE)
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { state?.rotate() },
                    enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = CropForeground, disabledContentColor = CropForeground.copy(alpha = 0.38f)),
                ) {
                    Icon(painterResource(Res.drawable.ic_rotate_right), contentDescription = stringResource(Res.string.article_image_crop_rotate))
                }
            }
        }
    }
}

@Composable
private fun AspectChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = CropForeground,
            selectedLabelColor = Color.Black,
            selectedContainerColor = CropForeground,
        ),
    )
}

/** What the user is editing: the crop rectangle in the displayed frame, the rotation and the aspect lock. */
private class CropState(private val preview: CropPreview) {
    var quarterTurns by mutableStateOf(0)
        private set
    var aspect by mutableStateOf(CropAspect.FREE)
        private set
    var rect by mutableStateOf(initialCrop(preview.width.toFloat(), preview.height.toFloat(), CropAspect.FREE))
        private set

    val frameWidth: Float get() = if (quarterTurns % 2 == 0) preview.width.toFloat() else preview.height.toFloat()
    val frameHeight: Float get() = if (quarterTurns % 2 == 0) preview.height.toFloat() else preview.width.toFloat()

    fun selectAspect(value: CropAspect) {
        aspect = value
        rect = withAspect(rect, value.ratio, frameWidth, frameHeight)
    }

    fun rotate() {
        rect = rotateRect90(rect, frameHeight)
        quarterTurns = (quarterTurns + 1) % 4
    }

    fun drag(handle: CropHandle, dx: Float, dy: Float) {
        rect = dragCrop(rect, handle, dx, dy, frameWidth, frameHeight, aspect.ratio, minCropSize(frameWidth, frameHeight))
    }

    fun request() = CropRequest(
        box = toCropBox(rect, frameWidth.toInt(), frameHeight.toInt()),
        quarterTurns = quarterTurns,
    )
}

@Composable
private fun rememberCropState(preview: CropPreview): CropState = remember(preview) { CropState(preview) }

@Composable
private fun CropCanvas(preview: CropPreview, state: CropState, enabled: Boolean) {
    val density = LocalDensity.current
    val marginPx = with(density) { 24.dp.toPx() }
    val touchPx = with(density) { 32.dp.toPx() }
    val strokePx = with(density) { 2.dp.toPx() }
    val handlePx = with(density) { 4.dp.toPx() }
    val handleLenPx = with(density) { 20.dp.toPx() }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    val description = stringResource(Res.string.article_image_crop_area)

    fun viewport() = fitViewport(state.frameWidth, state.frameHeight, viewSize.width, viewSize.height, marginPx)

    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var active: CropHandle? = null
                detectDragGestures(
                    onDragStart = { start ->
                        val vp = viewport()
                        active = hitTest(state.rect, vp.toImageX(start.x), vp.toImageY(start.y), touchPx / vp.scale)
                    },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, drag ->
                    val handle = active ?: return@detectDragGestures
                    change.consume()
                    val scale = viewport().scale
                    state.drag(handle, drag.x / scale, drag.y / scale)
                }
            },
    ) {
        viewSize = size
        val vp = viewport()
        val turns = state.quarterTurns

        // The photo, turned around the center of the displayed frame
        val drawW = (preview.width * vp.scale).toInt().coerceAtLeast(1)
        val drawH = (preview.height * vp.scale).toInt().coerceAtLeast(1)
        val centerX = vp.toViewX(state.frameWidth / 2f)
        val centerY = vp.toViewY(state.frameHeight / 2f)
        withTransform({
            translate(centerX, centerY)
            rotate(turns * 90f, Offset.Zero)
        }) {
            drawImage(preview.bitmap, dstOffset = IntOffset(-drawW / 2, -drawH / 2), dstSize = IntSize(drawW, drawH))
        }

        val left = vp.toViewX(state.rect.left)
        val top = vp.toViewY(state.rect.top)
        val right = vp.toViewX(state.rect.right)
        val bottom = vp.toViewY(state.rect.bottom)

        // Dimmed outside
        val dim = Color.Black.copy(alpha = 0.6f)
        drawRect(dim, Offset.Zero, Size(size.width, top))
        drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(dim, Offset(0f, top), Size(left, bottom - top))
        drawRect(dim, Offset(right, top), Size(size.width - right, bottom - top))

        // Frame, thirds, handles
        val line = Color.White.copy(alpha = 0.9f)
        drawRect(line, Offset(left, top), Size(right - left, bottom - top), style = Stroke(strokePx / 2f))
        val grid = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = left + (right - left) * i / 3f
            val y = top + (bottom - top) * i / 3f
            drawLine(grid, Offset(x, top), Offset(x, bottom), strokeWidth = strokePx / 2f)
            drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = strokePx / 2f)
        }
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f
        val len = minOf(handleLenPx, (right - left) / 3f, (bottom - top) / 3f)
        // Corners: L shapes outside the rectangle line
        corner(line, left, top, 1f, 1f, len, handlePx)
        corner(line, right, top, -1f, 1f, len, handlePx)
        corner(line, left, bottom, 1f, -1f, len, handlePx)
        corner(line, right, bottom, -1f, -1f, len, handlePx)
        // Edges: short bars at the middle
        drawLine(line, Offset(midX - len / 2, top), Offset(midX + len / 2, top), strokeWidth = handlePx)
        drawLine(line, Offset(midX - len / 2, bottom), Offset(midX + len / 2, bottom), strokeWidth = handlePx)
        drawLine(line, Offset(left, midY - len / 2), Offset(left, midY + len / 2), strokeWidth = handlePx)
        drawLine(line, Offset(right, midY - len / 2), Offset(right, midY + len / 2), strokeWidth = handlePx)
    }
}

private fun DrawScope.corner(
    color: Color,
    x: Float,
    y: Float,
    dirX: Float,
    dirY: Float,
    len: Float,
    width: Float,
) {
    drawLine(color, Offset(x - dirX * width / 2, y), Offset(x + dirX * len, y), strokeWidth = width)
    drawLine(color, Offset(x, y - dirY * width / 2), Offset(x, y + dirY * len), strokeWidth = width)
}
