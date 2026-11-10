package ch.mathorck.easystock.images

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

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

/** The image to crop. */
sealed interface CropSource {
    /** A photo or gallery image just copied by the [ImagePicker], in temporary storage. At most one at a time. */
    data object Pending : CropSource

    /** The stored photo at [path] (see [newImagePath]). Never modified: the crop makes a new file. */
    data class Stored(val path: String) : CropSource
}

/** [bitmap] is a sampled preview of an image [width] x [height] pixels, upright. */
class CropPreview(val bitmap: ImageBitmap, val width: Int, val height: Int)

/**
 * What to keep: [box] in the displayed image (the upright photo turned [quarterTurns] x 90 degrees
 * clockwise), in full-size pixels.
 */
data class CropRequest(val box: CropBox, val quarterTurns: Int)

/** The single photo store of the app. */
@Composable
expect fun rememberArticleImageStore(): ArticleImageStore

enum class ImageError {
    /** The image could not be read or saved. */
    FAILED,

    /** No camera app on the device. */
    NO_CAMERA,

    /** The camera permission was refused. */
    CAMERA_DENIED,
}

/** Takes a photo or picks one from the gallery, then copies it into temporary storage for the crop screen. */
interface ImagePicker {
    /** True while a photo is being copied. */
    val busy: Boolean

    fun takePhoto()

    fun pickFromGallery()
}

/**
 * [onPicked] is called with [CropSource.Pending] once the image is copied. The caller then crops it,
 * which saves or discards it (see [ArticleImageStore.saveCrop]).
 */
@Composable
expect fun rememberImagePicker(onPicked: (CropSource) -> Unit, onError: (ImageError) -> Unit): ImagePicker
