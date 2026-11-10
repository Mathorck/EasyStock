package ch.mathorck.easystock.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.db.BackupDao
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.images.IMAGE_DIR
import ch.mathorck.easystock.images.isValidImagePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.UUID
import kotlin.time.Clock

/** Largest backup file accepted (download or opened file). */
const val MAX_BACKUP_FILE_BYTES = 2L * 1024 * 1024 * 1024

/**
 * Android backups. Temporary files live in `cacheDir/backup` and are deleted by the discard methods
 * (and when the system clears the cache). Nothing is logged.
 */
class AndroidBackupService(
    context: Context,
    private val dao: BackupDao,
    private val filesDir: File = context.applicationContext.filesDir,
    private val workDir: File = File(context.applicationContext.cacheDir, "backup"),
    private val clock: Clock = Clock.System,
) : BackupService {
    private val appContext = context.applicationContext

    constructor(context: Context, database: AppDatabase) : this(context, database.backupDao())

    private fun newWorkFile(suffix: String): File {
        workDir.mkdirs()
        return File(workDir, "${UUID.randomUUID()}$suffix")
    }

    private fun appVersion(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    override suspend fun export(password: CharArray?): ExportedBackup = io {
        val data = dao.snapshot()
        val now = clock.now()
        val manifest = BackupManifest(appVersion = appVersion(), createdAt = now.toString())
        val file = newWorkFile(".$BACKUP_FILE_EXTENSION")
        val secret = password?.takeIf { it.isNotEmpty() }?.let { BackupSecret.Password(it.copyOf()) }
        try {
            val photos = FileOutputStream(file).use { out ->
                val buffered = out.buffered(64 * 1024)
                val count = if (secret == null) {
                    BackupArchive.write(buffered, manifest, data, filesDir)
                } else {
                    EncryptingOutputStream(BackupCipher.encryptingSink(buffered.asByteSink(), secret))
                        .use { BackupArchive.write(it, manifest, data, filesDir) }
                }
                buffered.flush()
                out.fd.sync()
                count
            }
            val counts = data.counts() + ("images" to photos)
            val qr = if (photos > 0) {
                QrDecision.TooLarge
            } else {
                val bundle = BackupBundle(manifest.copy(counts = counts), data)
                val plain = QrTransfer.buildPayload(bundle, null)
                // The envelope adds a fixed overhead: no need to derive the key when it can't fit
                when {
                    plain.size + QR_ENVELOPE_OVERHEAD > MAX_QR_PAYLOAD_BYTES && secret != null -> QrDecision.TooLarge
                    secret == null -> QrTransfer.decide(plain, hasPhotos = false)
                    else -> QrTransfer.decide(QrTransfer.buildPayload(bundle, secret), hasPhotos = false)
                }
            }
            val stamp = now.toString().take(16).replace("-", "").replace(":", "").replace("T", "-")
            ExportedBackup(
                path = file.path,
                fileName = "easystock-$stamp.$BACKUP_FILE_EXTENSION",
                sizeBytes = file.length(),
                manifest = manifest.copy(counts = counts),
                photos = photos,
                encrypted = secret != null,
                qr = qr,
            )
        } catch (e: Throwable) {
            file.delete()
            throw e
        } finally {
            secret?.chars?.fill('\u0000')
        }
    }

    override suspend fun startWifi(export: ExportedBackup): WifiSender = io {
        LanSender(File(export.path), localNetworkAddress() ?: throw BackupException(BackupError.NOT_ON_WIFI))
    }

    override suspend fun download(ticket: WifiTicket, onProgress: (Long) -> Unit): ImportInput.File = io {
        if (localNetworkAddress() == null && ticket.host != "127.0.0.1") throw BackupException(BackupError.NOT_ON_WIFI)
        val file = newWorkFile(".download")
        lanDownload(ticket, file, MAX_BACKUP_FILE_BYTES, onProgress)
        ImportInput.File(file.path)
    }

    override suspend fun needsPassword(input: ImportInput): Boolean = io {
        when (input) {
            is ImportInput.Qr -> BackupCipher.isEncrypted(input.payload) &&
                BackupCipher.readHeader(input.payload.asByteSource()).needsPassword
            is ImportInput.File -> {
                val file = File(input.path)
                when (backupKind(file)) {
                    BackupKind.ENCRYPTED -> file.inputStream().buffered().use { BackupCipher.readHeader(it.asByteSource()).needsPassword }
                    BackupKind.ZIP, BackupKind.QR_BUNDLE -> false
                    BackupKind.UNKNOWN -> throw BackupException(BackupError.NOT_A_BACKUP)
                }
            }
        }
    }

    override suspend fun open(input: ImportInput, password: CharArray?): OpenedBackup = io {
        val secret = password?.takeIf { it.isNotEmpty() }?.let { BackupSecret.Password(it.copyOf()) }
        try {
            when (input) {
                is ImportInput.Qr -> openQr(input.payload, secret)
                is ImportInput.File -> openFile(File(input.path), secret)
            }
        } finally {
            secret?.chars?.fill('\u0000')
        }
    }

    private fun openQr(payload: ByteArray, secret: BackupSecret?): OpenedBackup {
        val plain = if (BackupCipher.isEncrypted(payload)) {
            BackupCipher.decrypt(payload, secret ?: throw BackupException(BackupError.PASSWORD_REQUIRED))
        } else {
            payload
        }
        val bundle = QrTransfer.readBundle(plain)
        validateBackup(bundle.data)
        return OpenedBackup(bundle.manifest, bundle.data, photos = 0, archivePath = null)
    }

    private fun openFile(file: File, secret: BackupSecret?): OpenedBackup {
        if (file.length() > MAX_BACKUP_FILE_BYTES) throw BackupException(BackupError.TOO_LARGE)
        return when (backupKind(file)) {
            BackupKind.ZIP -> openArchive(file, copy = true)
            BackupKind.QR_BUNDLE -> openQr(file.readBytes(), secret)
            BackupKind.UNKNOWN -> throw BackupException(BackupError.NOT_A_BACKUP)
            BackupKind.ENCRYPTED -> {
                val decrypted = newWorkFile(".zip")
                try {
                    file.inputStream().buffered(64 * 1024).use { input ->
                        val source = input.asByteSource()
                        val header = BackupCipher.readHeader(source)
                        val key = BackupCipher.unlock(header, secret ?: throw BackupException(BackupError.PASSWORD_REQUIRED))
                        FileOutputStream(decrypted).buffered(64 * 1024).use { BackupCipher.decrypt(header, key, source, it.asByteSink()) }
                        key.fill(0)
                    }
                    when (backupKind(decrypted)) {
                        BackupKind.ZIP -> openArchive(decrypted, copy = false)
                        BackupKind.QR_BUNDLE -> openQr(decrypted.readBytes(), null).also { decrypted.delete() }
                        else -> throw BackupException(BackupError.NOT_A_BACKUP)
                    }
                } catch (e: Throwable) {
                    decrypted.delete()
                    throw e
                }
            }
        }
    }

    /** [copy]: the archive belongs to the input, the opened backup keeps its own copy (discarded separately). */
    private fun openArchive(file: File, copy: Boolean): OpenedBackup {
        val content = BackupArchive.read(file)
        val archive = if (copy) newWorkFile(".zip").also { file.copyTo(it, overwrite = true) } else file
        return OpenedBackup(content.manifest, content.data, content.photos.size, archive.path)
    }

    override suspend fun import(backup: OpenedBackup, mode: ImportMode): ImportSummary = io {
        validateBackup(backup.data)
        val archive = backup.archivePath?.let(::File)
        when (mode) {
            ImportMode.REPLACE -> replace(backup.data, archive)
            ImportMode.MERGE -> merge(backup.data, archive)
        }
    }

    private suspend fun replace(data: BackupData, archive: File?): ImportSummary {
        val images = File(filesDir, IMAGE_DIR)
        // Photos go to a new folder first, swapped in once the database is replaced
        val staging = File(filesDir, "$IMAGE_DIR.incoming").apply { deleteRecursively() }
        val old = File(filesDir, "$IMAGE_DIR.old").apply { deleteRecursively() }
        try {
            val photos = archive?.let { BackupArchive.extractPhotos(it, data.imagePaths(), staging) } ?: emptySet()
            File(staging, IMAGE_DIR).mkdirs()
            // Referenced photos already on this phone but not in the backup (QR code) are kept
            (data.imagePaths() - photos).filter { isValidImagePath(it) && File(filesDir, it).isFile }
                .forEach { File(filesDir, it).copyTo(File(staging, it), overwrite = true) }
            dao.replaceAll(data)
            swap(images, File(staging, IMAGE_DIR), old)
            return replaceSummary(data, photos.size)
        } finally {
            staging.deleteRecursively()
            old.deleteRecursively()
        }
    }

    /** Puts [incoming] in place of [images]. After the commit: never fails, falls back to copying. */
    private fun swap(images: File, incoming: File, old: File) {
        if (images.exists() && !images.renameTo(old)) {
            images.deleteRecursively()
        }
        if (!incoming.renameTo(images)) {
            images.mkdirs()
            incoming.listFiles()?.forEach { runCatching { it.copyTo(File(images, it.name), overwrite = true) } }
        }
    }

    private suspend fun merge(data: BackupData, archive: File?): ImportSummary {
        // Copied before the transaction (missing ones only); the unused ones are deleted after
        val missing = data.imagePaths().filterNot { File(filesDir, it).exists() }
        val copied = archive?.let { BackupArchive.extractPhotos(it, missing, filesDir) } ?: emptySet()
        val plan = try {
            dao.merge(data)
        } catch (e: Throwable) {
            copied.forEach { File(filesDir, it).delete() }
            throw e
        }
        val kept = plan.result.imagePaths()
        (copied - kept).forEach { File(filesDir, it).delete() }
        (plan.photosReleased - kept).forEach { File(filesDir, it).delete() }
        return plan.summary(photos = copied.count { it in kept })
    }

    /** Copies a picked file to the cache, so it can be read more than once. */
    suspend fun copyIn(open: () -> InputStream?): ImportInput.File = io {
        val file = newWorkFile(".import")
        try {
            (open() ?: throw BackupException(BackupError.FAILED, "Cannot open")).use { input ->
                FileOutputStream(file).use { copyBounded(input, it, MAX_BACKUP_FILE_BYTES) }
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        ImportInput.File(file.path)
    }

    /** Writes [export] to a file chosen by the user. */
    suspend fun copyOut(export: ExportedBackup, open: () -> OutputStream?) = io {
        (open() ?: throw BackupException(BackupError.FAILED, "Cannot open")).use { out -> File(export.path).inputStream().use { it.copyTo(out) } }
    }

    /** A copy of [export] named [ExportedBackup.fileName], in the folder shared through the FileProvider. */
    suspend fun shareCopy(export: ExportedBackup): File = io {
        val dir = File(workDir, "share").apply { deleteRecursively(); mkdirs() }
        File(export.path).copyTo(File(dir, export.fileName), overwrite = true)
    }

    override fun discard(export: ExportedBackup) {
        File(workDir, "share").deleteRecursively()
        File(export.path).takeIf { it.parentFile == workDir }?.delete()
    }

    override fun discard(input: ImportInput) {
        if (input is ImportInput.File) File(input.path).takeIf { it.parentFile == workDir }?.delete()
    }

    override fun discard(backup: OpenedBackup) {
        backup.archivePath?.let(::File)?.takeIf { it.parentFile == workDir }?.delete()
    }

    /** IPv4 address of this phone on a Wi-Fi or Ethernet network, null when there is none. */
    private fun localNetworkAddress(): InetAddress? {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val networks = listOfNotNull(manager.activeNetwork) + manager.allNetworks
        return networks.distinct().firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            val local = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!local) return@firstNotNullOfOrNull null
            manager.getLinkProperties(network)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && isLocalNetworkAddress(it.hostAddress ?: "") }
        }
    }

    /** I/O work; unexpected I/O errors become [BackupError.FAILED], out of memory [BackupError.TOO_LARGE]. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupException) {
            throw e
        } catch (e: IOException) {
            throw BackupException(BackupError.FAILED, "I/O", e)
        } catch (e: OutOfMemoryError) {
            throw BackupException(BackupError.TOO_LARGE, "Out of memory")
        }
    }
}

/** Header plus the tag of the single chunk: a QR payload is far below one chunk. */
private const val QR_ENVELOPE_OVERHEAD = 70

@Volatile
private var instance: AndroidBackupService? = null

fun backupService(context: Context): AndroidBackupService =
    instance ?: synchronized(AndroidBackupService::class) {
        instance ?: AndroidBackupService(context, appDatabase(context)).also { instance = it }
    }

@Composable
actual fun rememberBackupService(): BackupService {
    val context = LocalContext.current.applicationContext
    return remember(context) { backupService(context) }
}
