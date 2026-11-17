package ch.mathorck.easystock.services.images

import androidx.compose.ui.graphics.ImageBitmap

/** [bitmap] is a sampled preview of an image [width] x [height] pixels, upright. */
class CropPreview(val bitmap: ImageBitmap, val width: Int, val height: Int)
