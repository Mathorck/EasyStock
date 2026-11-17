package ch.mathorck.easystock.services.backup

actual object BackupCompression {
    actual fun deflate(bytes: ByteArray): ByteArray = unsupported()

    actual fun inflate(bytes: ByteArray, maxBytes: Int): ByteArray = unsupported()
}
