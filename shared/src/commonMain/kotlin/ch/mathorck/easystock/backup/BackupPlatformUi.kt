package ch.mathorck.easystock.backup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Modules of a QR code, row by row. [size] x [size]. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    init {
        require(modules.size == size * size)
    }

    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

/** QR code of [text], error correction level M. Null when it can't be encoded (too long, or no encoder). */
expect fun encodeQr(text: String): QrMatrix?

/** Quiet zone around the code, in modules (the standard asks for 4). */
private const val QUIET_ZONE = 4

/**
 * Draws a QR code, always dark on white with its quiet zone: scanners need it, also in dark theme.
 * Draws nothing when [text] can't be encoded.
 */
@Composable
fun QrCodeImage(text: String, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) } ?: return
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val total = matrix.size + 2 * QUIET_ZONE
        val module = size.minDimension / total
        drawRect(Color.White)
        for (y in 0 until matrix.size) {
            var x = 0
            while (x < matrix.size) {
                if (!matrix[x, y]) {
                    x++
                    continue
                }
                // One rectangle per run of dark modules; slightly wider to avoid hairline gaps
                val start = x
                while (x < matrix.size && matrix[x, y]) x++
                drawRect(
                    Color.Black,
                    topLeft = Offset((start + QUIET_ZONE) * module, (y + QUIET_ZONE) * module),
                    size = Size((x - start) * module + 0.5f, module + 0.5f),
                )
            }
        }
    }
}

/**
 * Camera preview that reports the first QR code read. Asks for the camera permission when shown
 * (and only then); shows a message with a way to allow it when refused.
 */
@Composable
expect fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier = Modifier)

/** Save, share and open backup files with the system pickers. */
interface BackupFiles {
    /** Lets the user choose where to save [export]. */
    fun save(export: ExportedBackup)

    /** Sends [export] to another app (mail, messenger, cloud) chosen by the user. */
    fun share(export: ExportedBackup)

    /** Lets the user pick a backup file; it is copied to the app cache before [onOpened]. */
    fun open()
}

@Composable
expect fun rememberBackupFiles(
    onOpened: (ImportInput.File) -> Unit,
    onSaved: () -> Unit,
    onError: (BackupError) -> Unit,
): BackupFiles
