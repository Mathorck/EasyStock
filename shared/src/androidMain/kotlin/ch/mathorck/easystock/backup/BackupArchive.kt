package ch.mathorck.easystock.backup

import ch.mathorck.easystock.images.isValidImagePath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Pure JVM (java.util.zip, java.io): runs on the device and in the host tests.

/** Archive content without the photos, which stay in the file until copied. */
class ArchiveContent(val manifest: BackupManifest, val data: BackupData, val photos: Set<String>)

/** Kind of a file or payload, from its first bytes. */
enum class BackupKind { ENCRYPTED, ZIP, QR_BUNDLE, UNKNOWN }

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

fun backupKind(prefix: ByteArray): BackupKind = when {
    BackupCipher.isEncrypted(prefix) -> BackupKind.ENCRYPTED
    startsWith(prefix, ZIP_MAGIC) -> BackupKind.ZIP
    startsWith(prefix, QR_BUNDLE_MAGIC) -> BackupKind.QR_BUNDLE
    else -> BackupKind.UNKNOWN
}

fun backupKind(file: File): BackupKind {
    val prefix = ByteArray(4)
    val n = FileInputStream(file).use { it.asByteSource().readFully(prefix) }
    return backupKind(prefix.copyOf(n))
}

object BackupArchive {
    /**
     * Writes the archive to [out] (not closed). Photos are read from [filesDir] (`images/<uuid>.jpg`);
     * a missing photo file is left out. Returns the number of photos written.
     */
    fun write(out: OutputStream, manifest: BackupManifest, data: BackupData, filesDir: File): Int {
        val photos = data.imagePaths().filter { isValidImagePath(it) && File(filesDir, it).isFile }.sorted()
        val zip = ZipOutputStream(out)
        zip.setLevel(6)
        fun entry(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        val counts = data.counts() + ("images" to photos.size)
        entry(MANIFEST_ENTRY, BackupCodec.encodeManifest(manifest.copy(counts = counts)))
        BackupTable.entries.forEach { entry(it.entryName, BackupCodec.encodeTable(data, it)) }
        photos.forEach { path ->
            // JPEG is already compressed
            zip.putNextEntry(ZipEntry(path).apply { method = ZipEntry.DEFLATED })
            FileInputStream(File(filesDir, path)).use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
        return photos.size
    }

    /** Reads and checks the manifest and tables of a plain archive. Throws [BackupException]. */
    fun read(file: File): ArchiveContent = open(file) { zip ->
        val manifest = BackupCodec.decodeManifest(
            zip.read(MANIFEST_ENTRY, MAX_MANIFEST_BYTES) ?: throw BackupException(BackupError.NOT_A_BACKUP, "No manifest"),
        )
        val data = BackupCodec.decodeTables { zip.read(it.entryName, MAX_TABLE_BYTES) }
        validateBackup(data)
        val photos = data.imagePaths().filter { zip.getEntry(it) != null }.toSet()
        ArchiveContent(manifest, data, photos)
    }

    /**
     * Copies the photos [paths] of the archive to [targetDir] (same relative paths), each through a
     * temporary file. Paths not in the archive or invalid are skipped. Returns the copied paths.
     */
    fun extractPhotos(file: File, paths: Collection<String>, targetDir: File): Set<String> = open(file) { zip ->
        paths.filter { isValidImagePath(it) }.mapNotNullTo(HashSet()) { path ->
            val entry = zip.getEntry(path) ?: return@mapNotNullTo null
            val target = File(targetDir, path)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            try {
                FileOutputStream(temp).use { out ->
                    zip.getInputStream(entry).use { copyBounded(it, out, MAX_IMAGE_BYTES) }
                    out.fd.sync()
                }
                if (!temp.renameTo(target)) throw IOException("Cannot save the photo")
            } finally {
                temp.delete()
            }
            path
        }
    }

    private fun <T> open(file: File, block: (ZipFile) -> T): T = try {
        ZipFile(file).use(block)
    } catch (e: ZipException) {
        throw BackupException(BackupError.DAMAGED, "Invalid zip", e)
    } catch (e: IOException) {
        throw BackupException(BackupError.DAMAGED, "Unreadable zip", e)
    }

    /** The entry content, null when missing. The size in the entry is not trusted. */
    private fun ZipFile.read(name: String, maxBytes: Long): ByteArray? {
        val entry = getEntry(name) ?: return null
        if (entry.isDirectory) throw BackupException(BackupError.DAMAGED, "Directory entry")
        val out = java.io.ByteArrayOutputStream()
        getInputStream(entry).use { copyBounded(it, out, maxBytes) }
        return out.toByteArray()
    }
}

/** Copies at most [maxBytes], throws [BackupException] ([BackupError.TOO_LARGE]) beyond. */
internal fun copyBounded(input: InputStream, out: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return total
        total += n
        if (total > maxBytes) throw BackupException(BackupError.TOO_LARGE, "Entry too large")
        out.write(buffer, 0, n)
    }
}

fun InputStream.asByteSource(): ByteSource = ByteSource { buffer, offset, length -> read(buffer, offset, length) }

fun OutputStream.asByteSink(): ByteSink = ByteSink { buffer, offset, length -> write(buffer, offset, length) }

/** An output stream encrypting into [sink]. [close] writes the last chunk, the underlying stream stays open. */
class EncryptingOutputStream(private val sink: EncryptingSink) : OutputStream() {
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) = sink.write(b, off, len)

    override fun close() {
        if (closed) return
        closed = true
        sink.finish()
    }
}
