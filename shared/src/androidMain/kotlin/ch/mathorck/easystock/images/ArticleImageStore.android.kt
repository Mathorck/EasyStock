package ch.mathorck.easystock.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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

    /** Temporary file the camera app writes to. Shared through the FileProvider (`cache/camera`). */
    fun captureFile(): File = File(appContext.cacheDir, "camera/capture.jpg").apply { parentFile?.mkdirs() }

    fun captureUri(file: File): Uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)

    /**
     * Copies an image into the photo folder: upright (EXIF orientation applied), at most
     * [MAX_IMAGE_SIDE] px, JPEG [IMAGE_JPEG_QUALITY]. [open] is called once per read.
     * Returns the relative path of the new file. Throws [IOException] if the image can't be read or saved.
     */
    suspend fun import(open: () -> InputStream?): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (open() ?: throw IOException("Cannot open the image")).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), MAX_IMAGE_SIDE)
        }
        val decoded = (open() ?: throw IOException("Cannot open the image"))
            .use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("Cannot decode the image")
        val orientation = try {
            open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: IOException) {
            null
        } ?: ExifInterface.ORIENTATION_NORMAL

        val (width, height) = scaledSize(decoded.width, decoded.height, MAX_IMAGE_SIDE)
        val matrix = Matrix().apply {
            postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
            applyOrientation(orientation)
        }
        val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

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
        } finally {
            if (output !== decoded) output.recycle()
            decoded.recycle()
        }
        path
    }

    private companion object {
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
