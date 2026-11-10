package ch.mathorck.easystock.images

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

// Unverified (no Mac). Photos are Android only for now: no image is shown, the buttons report an error.

private object NoArticleImageStore : ArticleImageStore {
    override fun cached(path: String, requiredPx: Int): ImageBitmap? = null

    override suspend fun load(path: String, requiredPx: Int): ImageBitmap? = null

    override suspend fun delete(path: String) = Unit
}

@Composable
actual fun rememberArticleImageStore(): ArticleImageStore = NoArticleImageStore

@Composable
actual fun rememberImagePicker(onImported: (String) -> Unit, onError: (ImageError) -> Unit): ImagePicker =
    object : ImagePicker {
        override val busy: Boolean = false

        override fun takePhoto() = onError(ImageError.FAILED)

        override fun pickFromGallery() = onError(ImageError.FAILED)
    }
