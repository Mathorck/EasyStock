package ch.mathorck.easystock.server

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import ch.mathorck.easystock.backup.encodeQr
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Files in `cache/server/share` (shared with the FileProvider, see androidApp res/xml/file_paths.xml).
 * Only one file at a time: a new one drops the others, so a server backup doesn't stay in the cache.
 */
class AndroidServerFileStore(context: Context) : ServerFileStore {
    private val dir = File(context.cacheDir, "server/share")

    private fun newFile(name: String): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        // A name from a picker may hold anything: keep a safe file name
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "file" }
        return File(dir, safe)
    }

    override suspend fun receive(name: String, mimeType: String, channel: ByteReadChannel): LocalFile =
        withContext(Dispatchers.IO) {
            val file = newFile(name)
            file.outputStream().use { channel.copyTo(it) }
            LocalFile(file.path, file.name, file.length(), mimeType)
        }

    override fun read(file: LocalFile): ByteReadChannel = File(file.path).inputStream().toByteReadChannel()

    override suspend fun invitePdf(name: String, qrText: String, serverName: String, line: String): LocalFile =
        withContext(Dispatchers.IO) {
            val file = newFile(name)
            file.outputStream().use { writeInvitePdf(it, qrText, serverName, line) }
            LocalFile(file.path, file.name, file.length(), PDF)
        }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Copies a picked document into the cache. */
    suspend fun copyIn(context: Context, uri: Uri): LocalFile = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "import"
        val file = newFile(name)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open the file")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        LocalFile(file.path, file.name, file.length(), "application/octet-stream")
    }

    companion object {
        const val PDF = "application/pdf"
    }
}

/** A4 in PostScript points. */
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 56f

/**
 * One A4 page: [serverName] on top, the QR code in the middle (drawn as vector squares, sharp when
 * printed), and [line] under it. No token as text.
 */
internal fun writeInvitePdf(out: java.io.OutputStream, qrText: String, serverName: String, line: String) {
    val matrix = encodeQr(qrText) ?: throw IOException("QR code too long")
    val pdf = PdfDocument()
    try {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        val textWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            isFakeBoldText = true
        }
        val titleLayout = StaticLayout.Builder.obtain(serverName, 0, serverName.length, title, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).build()
        canvas.save()
        canvas.translate(MARGIN, 90f)
        titleLayout.draw(canvas)
        canvas.restore()

        // QR code with its quiet zone (4 modules), 380 pt wide
        val quiet = 4
        val total = matrix.size + 2 * quiet
        val qrSize = 380f
        val module = qrSize / total
        val left = (PAGE_WIDTH - qrSize) / 2
        val top = 90f + titleLayout.height + 40f
        val black = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                val l = left + (x + quiet) * module
                val t = top + (y + quiet) * module
                canvas.drawRect(l, t, l + module, t + module, black)
            }
        }

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
        }
        val lineLayout = StaticLayout.Builder.obtain(line, 0, line.length, body, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        canvas.save()
        canvas.translate(MARGIN, top + qrSize + 32f)
        lineLayout.draw(canvas)
        canvas.restore()

        pdf.finishPage(page)
        pdf.writeTo(out)
    } finally {
        pdf.close()
    }
}

@Composable
actual fun rememberServerFiles(
    onPicked: (LocalFile) -> Unit,
    onSaved: () -> Unit,
    onFailed: () -> Unit,
): ServerFiles {
    val context = LocalContext.current
    val store = remember(context) { AndroidServerFileStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val saved by rememberUpdatedState(onSaved)
    val failed by rememberUpdatedState(onFailed)
    // The file to save once the user chose a place (kept while the picker is open)
    val pending = remember { mutableStateOf<LocalFile?>(null) }

    fun attempt(block: suspend CoroutineScope.() -> Unit) = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed()
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pending.value
        pending.value = null
        if (uri != null && file != null) {
            attempt {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write")
                    out.use { File(file.path).inputStream().use { input -> input.copyTo(it) } }
                }
                saved()
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attempt { picked(store.copyIn(context, uri)) }
    }

    return remember(store, create, open) {
        object : ServerFiles, ServerFileStore by store {
            override fun save(file: LocalFile) {
                pending.value = file
                try {
                    create.launch(file.name)
                } catch (e: ActivityNotFoundException) {
                    pending.value = null
                    failed()
                }
            }

            override fun share(file: LocalFile) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = file.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(file.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                } catch (e: ActivityNotFoundException) {
                    failed()
                } catch (e: IllegalArgumentException) {
                    failed()
                }
            }

            override fun pick() {
                try {
                    open.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    failed()
                }
            }
        }
    }
}
