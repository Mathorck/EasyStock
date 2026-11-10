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
}

/** The single photo store of the app. */
@Composable
expect fun rememberArticleImageStore(): ArticleImageStore

enum class ImageError {
    /** The image could not be read or saved. */
    FAILED,

    /** No camera app on the device. */
    NO_CAMERA,
}

/** Takes a photo or picks one from the gallery, then copies it into the app storage. */
interface ImagePicker {
    /** True while a photo is being copied. */
    val busy: Boolean

    fun takePhoto()

    fun pickFromGallery()
}

/**
 * [onImported] receives the path of the new file (see [newImagePath]). It is always called once
 * the copy is done, also after the caller left the screen: the caller deletes the file if unused.
 */
@Composable
expect fun rememberImagePicker(onImported: (String) -> Unit, onError: (ImageError) -> Unit): ImagePicker
