package ch.mathorck.easystock.images

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Article photos are JPEG files in app-private storage (filesDir on Android).
// `article.image_path` holds the path relative to that folder, e.g. "images/<uuid>.jpg".
// A new file is written for every photo, never overwritten: a path always points to the same image.

/** Folder of the article photos, relative to the app files folder. */
const val IMAGE_DIR = "images"

/** Longest side of a stored photo, in pixels. */
const val MAX_IMAGE_SIDE = 1600

/** JPEG quality of a stored photo (0-100). */
const val IMAGE_JPEG_QUALITY = 85

private val IMAGE_PATH = Regex("^$IMAGE_DIR/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$")

/** Path of a new photo file, relative to the app files folder. */
@OptIn(ExperimentalUuidApi::class)
fun newImagePath(id: Uuid = Uuid.random()): String = "$IMAGE_DIR/$id.jpg"

/**
 * True for a path made by [newImagePath]. Anything else (absolute path, "..", other folder)
 * is refused before a file is read or deleted.
 */
fun isValidImagePath(path: String): Boolean = IMAGE_PATH.matches(path)

/**
 * Largest power of two to decode an image side of [sidePx] with, keeping at least [requiredPx]
 * (BitmapFactory inSampleSize). 1 when the side is already small enough.
 */
fun sampleSizeFor(sidePx: Int, requiredPx: Int): Int {
    if (sidePx <= 0 || requiredPx <= 0) return 1
    var sample = 1
    while (sidePx / (sample * 2) >= requiredPx) sample *= 2
    return sample
}

/** Size of a [width] x [height] image shrunk to fit [maxSide] on its longest side. Never enlarged. */
fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toDouble() / longest
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/**
 * Photo files written while a form is open, not saved yet.
 * [stored]: path saved on the article when the form opened (null for a new article).
 */
data class StagedImages(val stored: String? = null, val staged: Set<String> = emptySet()) {
    operator fun plus(path: String): StagedImages = copy(staged = staged + path)

    /**
     * Files to delete once [saved] is stored: the other staged files, and the [previous] photo
     * when it was replaced or removed.
     */
    fun toDeleteOnSave(saved: String?, previous: String? = stored): Set<String> =
        (staged + listOfNotNull(previous)) - setOfNotNull(saved)

    /** Files to delete when the form is closed without saving: the stored photo stays. */
    fun toDeleteOnCancel(): Set<String> = staged - setOfNotNull(stored)
}
