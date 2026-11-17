package ch.mathorck.easystock.services.images

/** A whole-pixel crop box, [right] and [bottom] excluded. */
data class CropBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
