package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import ch.mathorck.easystock.images.CropSource
import ch.mathorck.easystock.images.ImageError
import ch.mathorck.easystock.images.rememberArticleImageStore
import ch.mathorck.easystock.images.rememberImagePicker
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.article_image_crop
import easystock.shared.generated.resources.article_image_description
import easystock.shared.generated.resources.article_image_error
import easystock.shared.generated.resources.article_image_loading
import easystock.shared.generated.resources.article_image_no_camera
import easystock.shared.generated.resources.article_image_camera_denied
import easystock.shared.generated.resources.article_image_none
import easystock.shared.generated.resources.article_image_pick
import easystock.shared.generated.resources.article_image_remove
import easystock.shared.generated.resources.article_image_take
import easystock.shared.generated.resources.ic_crop
import easystock.shared.generated.resources.ic_delete
import easystock.shared.generated.resources.ic_image
import easystock.shared.generated.resources.ic_photo_camera
import easystock.shared.generated.resources.ic_photo_library
import ch.mathorck.easystock.sync.SyncImages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The photo at [path], cropped to fill the box by default (or [contentScale]), or [placeholder] while loading or without photo.
 * Decoded off the main thread at about the size of the box.
 */
@Composable
fun ArticleImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val store = rememberArticleImageStore()
        // Short side of the decoded image: enough to cover the box when cropped
        val side = if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) max(maxWidth, maxHeight) else 400.dp
        val requiredPx = with(LocalDensity.current) { side.roundToPx() }
        val bitmap: ImageBitmap? by produceState(path?.let { store.cached(it, requiredPx) }, path, requiredPx) {
            value = path?.let { store.load(it, requiredPx) }
            // Server mode: a pulled photo may still be downloading, loaded once it is there
            if (value == null && path != null) {
                SyncImages.downloads.first { store.load(path, requiredPx)?.also { value = it } != null }
            }
        }
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription, contentScale = contentScale, modifier = Modifier.matchParentSize())
        } else {
            placeholder()
        }
    }
}

/** Small square photo for list rows, with an icon when there is none. */
@Composable
fun ArticleThumbnail(path: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    ArticleImage(
        path = path,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        placeholder = {
            Icon(
                painterResource(Res.drawable.ic_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        },
    )
}

/**
 * Photo of an article with its actions: take a photo, pick one from the gallery (both open the
 * crop screen first), crop the current photo, remove it.
 * [onImported] receives the path of the new file once the crop is saved.
 */
@Composable
fun ArticleImageEditor(
    path: String?,
    onImported: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    var error by remember { mutableStateOf<ImageError?>(null) }
    // What is being cropped: "" for the image just picked, else the stored path. Survives rotation of the device.
    var cropKey by rememberSaveable { mutableStateOf<String?>(null) }
    val store = rememberArticleImageStore()
    val scope = rememberCoroutineScope()
    val picker = rememberImagePicker(
        onPicked = {
            error = null
            cropKey = (it as? CropSource.Stored)?.path ?: ""
        },
        onError = { error = it },
    )
    cropKey?.let { key ->
        val source = if (key.isEmpty()) CropSource.Pending else CropSource.Stored(key)
        fun close(discard: Boolean) {
            cropKey = null
            if (discard) scope.launch { store.discardCrop(source) }
        }
        CropDialog(
            source = source,
            onCancel = { close(discard = true) },
            onSaved = {
                close(discard = false)
                error = null
                onImported(it)
            },
            onError = {
                close(discard = true)
                error = ImageError.FAILED
            },
        )
    }
    val loadingText = stringResource(Res.string.article_image_loading)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            ArticleImage(
                path = path,
                contentDescription = stringResource(Res.string.article_image_description),
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit,
                placeholder = { if (path == null) NoImage() },
            )
            if (picker.busy) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .semantics { contentDescription = loadingText },
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Res.drawable.ic_photo_camera, stringResource(Res.string.article_image_take), enabled = !picker.busy) {
                error = null
                picker.takePhoto()
            }
            ActionButton(Res.drawable.ic_photo_library, stringResource(Res.string.article_image_pick), enabled = !picker.busy) {
                error = null
                picker.pickFromGallery()
            }
            if (path != null) {
                ActionButton(Res.drawable.ic_crop, stringResource(Res.string.article_image_crop), enabled = !picker.busy) {
                    error = null
                    cropKey = path
                }
                TextButton(onClick = onRemove, enabled = !picker.busy) {
                    Icon(painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.article_image_remove))
                }
            }
        }
        error?.let {
            Text(
                stringResource(
                    when (it) {
                        ImageError.NO_CAMERA -> Res.string.article_image_no_camera
                        ImageError.CAMERA_DENIED -> Res.string.article_image_camera_denied
                        ImageError.FAILED -> Res.string.article_image_error
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NoImage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painterResource(Res.drawable.ic_image),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            stringResource(Res.string.article_image_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionButton(icon: DrawableResource, text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}
