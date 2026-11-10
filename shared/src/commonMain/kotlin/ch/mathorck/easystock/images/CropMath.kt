package ch.mathorck.easystock.images

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Crop rectangle math, free of any UI or platform type.
// All rectangles are in image pixels of the *displayed* frame: the photo upright (EXIF applied)
// and rotated by the user. The platform maps the final box back to the stored file with [sourceBox].

/** Smallest crop side, in image pixels. */
const val MIN_CROP_PX = 48f

/** A rectangle in image pixels. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** A whole-pixel crop box, [right] and [bottom] excluded. */
data class CropBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class CropAspect(val ratio: Float?) {
    FREE(null),
    SQUARE(1f),
}

enum class CropHandle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE }

private val CropHandle.movesLeft get() = this == CropHandle.TOP_LEFT || this == CropHandle.LEFT || this == CropHandle.BOTTOM_LEFT
private val CropHandle.movesRight get() = this == CropHandle.TOP_RIGHT || this == CropHandle.RIGHT || this == CropHandle.BOTTOM_RIGHT
private val CropHandle.movesTop get() = this == CropHandle.TOP_LEFT || this == CropHandle.TOP || this == CropHandle.TOP_RIGHT
private val CropHandle.movesBottom get() = this == CropHandle.BOTTOM_LEFT || this == CropHandle.BOTTOM || this == CropHandle.BOTTOM_RIGHT

/** Smallest crop side for a [width] x [height] image: [MIN_CROP_PX], or the image itself when smaller. */
fun minCropSize(width: Float, height: Float): Float = min(MIN_CROP_PX, min(width, height))

/** The crop shown when the screen opens: the whole image, or its largest centered rectangle of the aspect. */
fun initialCrop(width: Float, height: Float, aspect: CropAspect): CropRect =
    withAspect(CropRect(0f, 0f, width, height), aspect.ratio, width, height)

/**
 * Fits [rect] to [ratio] (width / height), keeping its center: the largest rectangle of that ratio
 * inside [rect]. A null [ratio] keeps [rect].
 */
fun withAspect(rect: CropRect, ratio: Float?, width: Float, height: Float): CropRect {
    if (ratio == null) return rect
    var w = rect.width
    var h = rect.height
    if (w / h > ratio) w = h * ratio else h = w / ratio
    val minW = max(minCropSize(width, height), minCropSize(width, height) * ratio)
    if (w < minW) {
        // Only reachable with a very thin rect: grow back to the minimum
        h *= minW / w
        w = minW
    }
    w = w.coerceAtMost(width)
    h = h.coerceAtMost(height)
    return centeredIn(rect.centerX, rect.centerY, w, h, width, height)
}

private fun centeredIn(cx: Float, cy: Float, w: Float, h: Float, maxW: Float, maxH: Float): CropRect {
    val left = (cx - w / 2f).coerceIn(0f, maxW - w)
    val top = (cy - h / 2f).coerceIn(0f, maxH - h)
    return CropRect(left, top, left + w, top + h)
}

/**
 * Which part of [rect] is under ([x], [y]) within [touch] pixels: a corner, an edge, the inside
 * ([CropHandle.MOVE]), or null outside. Corners win over edges.
 */
fun hitTest(rect: CropRect, x: Float, y: Float, touch: Float): CropHandle? {
    // On a small rect the handles would cover everything: the grab zone is capped to a third of the side
    val tx = min(touch, rect.width / 3f)
    val ty = min(touch, rect.height / 3f)
    val nearLeft = abs(x - rect.left) <= tx
    val nearRight = abs(x - rect.right) <= tx
    val nearTop = abs(y - rect.top) <= ty
    val nearBottom = abs(y - rect.bottom) <= ty
    val inX = x >= rect.left - tx && x <= rect.right + tx
    val inY = y >= rect.top - ty && y <= rect.bottom + ty
    return when {
        nearTop && nearLeft -> CropHandle.TOP_LEFT
        nearTop && nearRight -> CropHandle.TOP_RIGHT
        nearBottom && nearLeft -> CropHandle.BOTTOM_LEFT
        nearBottom && nearRight -> CropHandle.BOTTOM_RIGHT
        nearTop && inX -> CropHandle.TOP
        nearBottom && inX -> CropHandle.BOTTOM
        nearLeft && inY -> CropHandle.LEFT
        nearRight && inY -> CropHandle.RIGHT
        x > rect.left && x < rect.right && y > rect.top && y < rect.bottom -> CropHandle.MOVE
        else -> null
    }
}

/**
 * Applies a drag of ([dx], [dy]) image pixels on [handle]. The result stays inside the
 * [width] x [height] image, at least [minSize] on each side, and keeps [ratio] (width / height)
 * when not null. Locked corners keep the opposite corner fixed, locked edges keep the opposite edge
 * and the center of the other axis.
 */
fun dragCrop(
    rect: CropRect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    width: Float,
    height: Float,
    ratio: Float? = null,
    minSize: Float = minCropSize(width, height),
): CropRect {
    if (handle == CropHandle.MOVE) {
        val left = (rect.left + dx).coerceIn(0f, max(0f, width - rect.width))
        val top = (rect.top + dy).coerceIn(0f, max(0f, height - rect.height))
        return CropRect(left, top, left + rect.width, top + rect.height)
    }
    if (ratio == null) {
        var l = rect.left
        var t = rect.top
        var r = rect.right
        var b = rect.bottom
        if (handle.movesLeft) l = (l + dx).coerceIn(0f, r - minSize)
        if (handle.movesRight) r = (r + dx).coerceIn(l + minSize, width)
        if (handle.movesTop) t = (t + dy).coerceIn(0f, b - minSize)
        if (handle.movesBottom) b = (b + dy).coerceIn(t + minSize, height)
        return CropRect(l, t, r, b)
    }
    // Smallest width such that both sides reach minSize
    val minW = max(minSize, minSize * ratio)
    val horizontal = handle.movesLeft || handle.movesRight
    val vertical = handle.movesTop || handle.movesBottom
    if (horizontal && vertical) {
        val anchorX = if (handle.movesLeft) rect.right else rect.left
        val anchorY = if (handle.movesTop) rect.bottom else rect.top
        val proposedW = rect.width + (if (handle.movesLeft) -dx else dx)
        val proposedH = rect.height + (if (handle.movesTop) -dy else dy)
        val roomW = if (handle.movesLeft) anchorX else width - anchorX
        val roomH = if (handle.movesTop) anchorY else height - anchorY
        val w = ((proposedW + proposedH * ratio) / 2f).coerceIn(minW, max(minW, min(roomW, roomH * ratio)))
        val h = w / ratio
        val left = if (handle.movesLeft) anchorX - w else anchorX
        val top = if (handle.movesTop) anchorY - h else anchorY
        return CropRect(left, top, left + w, top + h)
    }
    if (horizontal) {
        val anchorX = if (handle.movesLeft) rect.right else rect.left
        val room = if (handle.movesLeft) anchorX else width - anchorX
        val maxW = min(room, 2f * min(rect.centerY, height - rect.centerY) * ratio)
        val w = (rect.width + (if (handle.movesLeft) -dx else dx)).coerceIn(minW, max(minW, maxW))
        val h = w / ratio
        val left = if (handle.movesLeft) anchorX - w else anchorX
        return CropRect(left, rect.centerY - h / 2f, left + w, rect.centerY + h / 2f)
    }
    val anchorY = if (handle.movesTop) rect.bottom else rect.top
    val room = if (handle.movesTop) anchorY else height - anchorY
    val maxH = min(room, 2f * min(rect.centerX, width - rect.centerX) / ratio)
    val minH = minW / ratio
    val h = (rect.height + (if (handle.movesTop) -dy else dy)).coerceIn(minH, max(minH, maxH))
    val w = h * ratio
    val top = if (handle.movesTop) anchorY - h else anchorY
    return CropRect(rect.centerX - w / 2f, top, rect.centerX + w / 2f, top + h)
}

/** [rect] after turning an image of the given [height] 90 degrees clockwise. The image becomes [height] wide. */
fun rotateRect90(rect: CropRect, height: Float): CropRect =
    CropRect(height - rect.bottom, rect.left, height - rect.top, rect.right)

/** Where the image is drawn in a view: [scale] view pixels per image pixel, top-left at ([offsetX], [offsetY]). */
data class CropViewport(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toImageX(viewX: Float) = (viewX - offsetX) / scale
    fun toImageY(viewY: Float) = (viewY - offsetY) / scale
    fun toViewX(imageX: Float) = imageX * scale + offsetX
    fun toViewY(imageY: Float) = imageY * scale + offsetY
}

/** The image fitted and centered in a [viewWidth] x [viewHeight] view, keeping a [margin] around it. */
fun fitViewport(imageWidth: Float, imageHeight: Float, viewWidth: Float, viewHeight: Float, margin: Float = 0f): CropViewport {
    val availW = max(1f, viewWidth - 2 * margin)
    val availH = max(1f, viewHeight - 2 * margin)
    val scale = min(availW / imageWidth, availH / imageHeight)
    return CropViewport(scale, (viewWidth - imageWidth * scale) / 2f, (viewHeight - imageHeight * scale) / 2f)
}

/** Whole-pixel box covering [rect], clamped to the image, at least 1 pixel on each side. */
fun toCropBox(rect: CropRect, width: Int, height: Int): CropBox {
    val left = floor(rect.left).toInt().coerceIn(0, width - 1)
    val top = floor(rect.top).toInt().coerceIn(0, height - 1)
    val right = ceil(rect.right).toInt().coerceIn(left + 1, width)
    val bottom = ceil(rect.bottom).toInt().coerceIn(top + 1, height)
    return CropBox(left, top, right, bottom)
}

// --- Orientation ---------------------------------------------------------------------------------
// EXIF orientation values (1..8) describe how a stored image is turned to show it upright. They also
// describe the user rotation (see [quarterTurnsOrientation]). The point maps below are the same
// transforms as the Android matrices applied to the decoded image.

private fun swapsAxes(orientation: Int) = orientation in 5..8

/** Size of a [width] x [height] image once shown with [orientation]. */
fun orientedSize(orientation: Int, width: Int, height: Int): Pair<Int, Int> =
    if (swapsAxes(orientation)) height to width else width to height

/** EXIF orientation of a clockwise rotation by [quarterTurns] x 90 degrees. */
fun quarterTurnsOrientation(quarterTurns: Int): Int = when (((quarterTurns % 4) + 4) % 4) {
    1 -> 6
    2 -> 3
    3 -> 8
    else -> 1
}

/** The orientation that undoes [orientation]. */
fun inverseOrientation(orientation: Int): Int = when (orientation) {
    6 -> 8
    8 -> 6
    else -> orientation
}

private fun mapX(orientation: Int, x: Int, y: Int, w: Int, h: Int): Int = when (orientation) {
    2, 3 -> w - x
    5, 8 -> y
    6, 7 -> h - y
    else -> x
}

private fun mapY(orientation: Int, x: Int, y: Int, w: Int, h: Int): Int = when (orientation) {
    3, 4 -> h - y
    5, 6 -> x
    7, 8 -> w - x
    else -> y
}

/** [box], in a [width] x [height] frame, seen in the frame obtained by applying [orientation]. */
fun mapBox(orientation: Int, box: CropBox, width: Int, height: Int): CropBox {
    val xs = listOf(box.left, box.right)
    val ys = listOf(box.top, box.bottom)
    val mx = xs.flatMap { x -> ys.map { y -> mapX(orientation, x, y, width, height) } }
    val my = xs.flatMap { x -> ys.map { y -> mapY(orientation, x, y, width, height) } }
    return CropBox(mx.min(), my.min(), mx.max(), my.max())
}

/**
 * Maps a [box] chosen on the displayed image back to the stored file.
 * The displayed image is the file ([fileWidth] x [fileHeight], as stored) turned by its EXIF
 * [exifOrientation], then by [quarterTurns] clockwise. Result in stored-file pixels.
 */
fun sourceBox(box: CropBox, fileWidth: Int, fileHeight: Int, exifOrientation: Int, quarterTurns: Int): CropBox {
    val (uprightW, uprightH) = orientedSize(exifOrientation, fileWidth, fileHeight)
    val userOrientation = quarterTurnsOrientation(quarterTurns)
    val (shownW, shownH) = orientedSize(userOrientation, uprightW, uprightH)
    require(box.right <= shownW && box.bottom <= shownH) { "Box outside the image" }
    val upright = mapBox(inverseOrientation(userOrientation), box, shownW, shownH)
    return mapBox(inverseOrientation(exifOrientation), upright, uprightW, uprightH)
}
