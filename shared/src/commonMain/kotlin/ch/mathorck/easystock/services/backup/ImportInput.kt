package ch.mathorck.easystock.services.backup

/** A backup to import, before it is opened. */
sealed interface ImportInput {
    /** A file copied to the app cache (opened file or Wi-Fi download). */
    class File(val path: String) : ImportInput

    /** A scanned data QR code. */
    class Qr(val payload: ByteArray) : ImportInput
}
