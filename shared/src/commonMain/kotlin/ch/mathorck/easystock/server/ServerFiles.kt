package ch.mathorck.easystock.server

import androidx.compose.runtime.Composable
import io.ktor.utils.io.ByteReadChannel

/** A file in the app cache (`cache/server/share`), made for the user to save or share. */
data class LocalFile(val path: String, val name: String, val size: Long, val mimeType: String)

/** Writes and reads the temporary files of the admin screens. Plain files, no system picker. */
interface ServerFileStore {
    /** Copies [channel] into a new cache file named [name]. */
    suspend fun receive(name: String, mimeType: String, channel: ByteReadChannel): LocalFile

    /** Reads [file] as a stream (for an upload). */
    fun read(file: LocalFile): ByteReadChannel

    /**
     * One A4 page with the QR code of [qrText], [serverName] and the localized [line]. The token is only in
     * the QR code, never as text.
     */
    suspend fun invitePdf(name: String, qrText: String, serverName: String, line: String): LocalFile

    /** Drops the temporary files. */
    suspend fun clear()
}

/** Save, share and pick files with the system pickers. */
interface ServerFiles : ServerFileStore {
    /** Lets the user choose where to save [file]. */
    fun save(file: LocalFile)

    /** Sends [file] to another app with the share sheet. */
    fun share(file: LocalFile)

    /** Lets the user pick a file; it is copied to the cache before `onPicked`. */
    fun pick()
}

/** Platform files of the admin screens. [onFailed] gets file errors (no space, no picker...). */
@Composable
expect fun rememberServerFiles(
    onPicked: (LocalFile) -> Unit,
    onSaved: () -> Unit,
    onFailed: () -> Unit,
): ServerFiles
