package ch.mathorck.easystock.services.images

/** Where the image is drawn in a view: [scale] view pixels per image pixel, top-left at ([offsetX], [offsetY]). */
data class CropViewport(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toImageX(viewX: Float) = (viewX - offsetX) / scale
    fun toImageY(viewY: Float) = (viewY - offsetY) / scale
    fun toViewX(imageX: Float) = imageX * scale + offsetX
    fun toViewY(imageY: Float) = imageY * scale + offsetY
}
