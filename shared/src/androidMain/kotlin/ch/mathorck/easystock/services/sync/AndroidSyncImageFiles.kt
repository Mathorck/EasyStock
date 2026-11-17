package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.services.images.IMAGE_DIR
import ch.mathorck.easystock.services.images.isValidImagePath
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The photo files in `filesDir/images`, as the article image store reads them. */
internal class AndroidSyncImageFiles(private val filesDir: File) : SyncImageFiles {
    private fun fileOf(path: String): File? = if (isValidImagePath(path)) File(filesDir, path) else null

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        fileOf(path)?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { fileOf(path)?.isFile == true }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { fileOf(path)?.let { runCatching { it.delete() } } }
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = fileOf(path) ?: return@withContext
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.download")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) error("Could not store the photo")
        } finally {
            temp.delete()
        }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) { File(filesDir, IMAGE_DIR).deleteRecursively() }
    }
}
