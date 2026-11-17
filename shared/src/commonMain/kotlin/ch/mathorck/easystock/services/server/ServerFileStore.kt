package ch.mathorck.easystock.services.server

import io.ktor.utils.io.ByteReadChannel

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
