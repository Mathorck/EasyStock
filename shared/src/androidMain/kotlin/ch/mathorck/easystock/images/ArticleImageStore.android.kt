package ch.mathorck.easystock.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/** Photos in `filesDir/images`, decoded images kept in a small memory cache. */
class AndroidArticleImageStore(context: Context) : ArticleImageStore {
    private val appContext = context.applicationContext
    private val filesDir: File = appContext.filesDir

    // Keyed by path and size. A path never changes content (see newImagePath), so no invalidation needed.
    private val memory = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount / 1024
    }

    private fun key(path: String, requiredPx: Int) = "$path@$requiredPx"

    /** The file of a valid relative path, null otherwise. */
    private fun fileOf(path: String): File? = if (isValidImagePath(path)) File(filesDir, path) else null

    override fun cached(path: String, requiredPx: Int): ImageBitmap? = memory.get(key(path, requiredPx))

    override suspend fun load(path: String, requiredPx: Int): ImageBitmap? {
        cached(path, requiredPx)?.let { return it }
        val file = fileOf(path) ?: return null
        return withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(min(bounds.outWidth, bounds.outHeight), requiredPx)
            }
            BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()?.also { memory.put(key(path, requiredPx), it) }
        }
    }

    override suspend fun delete(path: String) {
        val file = fileOf(path) ?: return
        withContext(Dispatchers.IO) { file.delete() }
        memory.snapshot().keys.filter { it.startsWith("$path@") }.forEach { memory.remove(it) }
    }

    /**
     * Temporary file the camera app writes to, shared through the FileProvider (`cache/camera`).
     * Also holds the gallery image waiting for the crop ([CropSource.Pending]).
     */
    fun captureFile(): File = File(appContext.cacheDir, "camera/capture.jpg").apply { parentFile?.mkdirs() }

    fun captureUri(file: File): Uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)

    /** Copies the stream to the pending file (temporary storage, replaced each time). Throws [IOException]. */
    suspend fun copyToPending(open: () -> InputStream?) = withContext(Dispatchers.IO) {
        val target = captureFile()
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            (open() ?: throw IOException("Cannot open the image")).use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(target)) throw IOException("Cannot copy the image")
        } finally {
            temp.delete()
        }
    }

    private fun fileOf(source: CropSource): File? = when (source) {
        CropSource.Pending -> captureFile().takeIf { it.isFile }
        is CropSource.Stored -> fileOf(source.path)?.takeIf { it.isFile }
    }

    /** Size as stored and EXIF orientation of an image file, null if it isn't a readable image. */
    private fun inspect(file: File): Triple<Int, Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return Triple(bounds.outWidth, bounds.outHeight, orientation)
    }

    override suspend fun openCrop(source: CropSource): CropPreview? = withContext(Dispatchers.IO) {
        val file = fileOf(source) ?: return@withContext null
        val (fileW, fileH, orientation) = inspect(file) ?: return@withContext null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(maxOf(fileW, fileH), CROP_PREVIEW_PX) }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return@withContext null
        val matrix = Matrix().apply { applyOrientation(orientation) }
        val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        val (width, height) = orientedSize(orientation, fileW, fileH)
        CropPreview(upright.asImageBitmap(), width, height)
    }

    override suspend fun saveCrop(source: CropSource, crop: CropRequest): String = withContext(Dispatchers.IO) {
        val file = fileOf(source) ?: throw IOException("Cannot open the image")
        val (fileW, fileH, orientation) = inspect(file) ?: throw IOException("Not an image")
        val turns = ((crop.quarterTurns % 4) + 4) % 4
        // The box is in the displayed image (EXIF + user rotation): map it back to the stored pixels
        val raw = sourceBox(crop.box, fileW, fileH, orientation, turns)
        val (targetW, targetH) = scaledSize(raw.width, raw.height, MAX_IMAGE_SIDE)
        val decoded = decodeRegion(file, raw, sampleSizeFor(maxOf(raw.width, raw.height), MAX_IMAGE_SIDE))
        val matrix = Matrix().apply {
            postScale(targetW.toFloat() / decoded.width, targetH.toFloat() / decoded.height)
            applyOrientation(orientation)
            applyOrientation(quarterTurnsOrientation(turns))
        }
        val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val path = try {
            writeJpeg(output)
        } finally {
            if (output !== decoded) output.recycle()
            decoded.recycle()
        }
        if (source == CropSource.Pending) file.delete()
        path
    }

    override suspend fun discardCrop(source: CropSource) {
        if (source == CropSource.Pending) withContext(Dispatchers.IO) { captureFile().delete() }
    }

    /** Decodes only [box] of the file, sampled. Falls back to a full sampled decode if region decoding isn't supported. */
    private fun decodeRegion(file: File, box: CropBox, sample: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val region = try {
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(file.path, false)
            try {
                decoder?.decodeRegion(Rect(box.left, box.top, box.right, box.bottom), options)
            } finally {
                decoder?.recycle()
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        if (region != null) return region
        val full = BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Cannot decode the image")
        val x = box.left / sample
        val y = box.top / sample
        val cropped = Bitmap.createBitmap(
            full,
            x,
            y,
            (box.width / sample).coerceIn(1, full.width - x),
            (box.height / sample).coerceIn(1, full.height - y),
        )
        if (cropped !== full) full.recycle()
        return cropped
    }

    /** Writes [output] as a new photo file and returns its relative path. */
    private fun writeJpeg(output: Bitmap): String {
        val path = newImagePath()
        val target = File(filesDir, path)
        target.parentFile?.mkdirs()
        // Written to a temporary file first, so a crash never leaves a half-written photo
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                if (!output.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)) throw IOException("Cannot encode")
                out.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("Cannot save the image")
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        return path
    }

    private companion object {
        /** Long side of the crop preview, in pixels. */
        const val CROP_PREVIEW_PX = 1280

        fun cacheSizeKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
    }
}

/** Rotates / mirrors so the image is shown upright, after the scale already in the matrix. */
private fun Matrix.applyOrientation(orientation: Int) {
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            postRotate(90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            postRotate(270f)
            postScale(-1f, 1f)
        }
    }
}

@Volatile
private var instance: AndroidArticleImageStore? = null

/** Process-wide store, built once from the application context. */
fun articleImageStore(context: Context): AndroidArticleImageStore =
    instance ?: synchronized(AndroidArticleImageStore::class) {
        instance ?: AndroidArticleImageStore(context).also { instance = it }
    }

@Composable
actual fun rememberArticleImageStore(): ArticleImageStore = rememberAndroidImageStore()

@Composable
internal fun rememberAndroidImageStore(): AndroidArticleImageStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { articleImageStore(context) }
}
