package ch.mathorck.easystock.services.images

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import ch.mathorck.easystock.enums.images.ImageError

/** Reads and deletes the article photo files. Paths are relative, see [newImagePath]. */
interface ArticleImageStore {
    /** The image if it is already in memory, for a first frame without flicker. */
    fun cached(path: String, requiredPx: Int): ImageBitmap?

    /**
     * Decodes the photo off the main thread, sampled down so its short side keeps at least [requiredPx].
     * Null when the file is missing, unreadable or the path is invalid.
     */
    suspend fun load(path: String, requiredPx: Int): ImageBitmap?

    /** Deletes the file. Does nothing for an invalid path or a missing file. */
    suspend fun delete(path: String)

    /**
     * Decodes [source] upright for the crop screen, sampled down to a screen-sized preview.
     * Null when it can't be read.
     */
    suspend fun openCrop(source: CropSource): CropPreview?

    /**
     * Crops [source] and saves the result as a new photo: crop, then scale to [MAX_IMAGE_SIDE], then
     * JPEG [IMAGE_JPEG_QUALITY]. Returns the new path. Deletes a [CropSource.Pending] source, never a stored one.
     * Throws if the image can't be read or saved (a pending source is then kept).
     */
    suspend fun saveCrop(source: CropSource, crop: CropRequest): String

    /** Drops a [CropSource.Pending] source (the user cancelled). Does nothing for a stored photo. */
    suspend fun discardCrop(source: CropSource)
}

/** The single photo store of the app. */
@Composable
expect fun rememberArticleImageStore(): ArticleImageStore

/**
 * [onPicked] is called with [CropSource.Pending] once the image is copied. The caller then crops it,
 * which saves or discards it (see [ArticleImageStore.saveCrop]).
 */
@Composable
expect fun rememberImagePicker(onPicked: (CropSource) -> Unit, onError: (ImageError) -> Unit): ImagePicker
