package ch.mathorck.easystock.services.backup

expect object BackupCompression {
    /** Raw DEFLATE, best compression. */
    fun deflate(bytes: ByteArray): ByteArray

    /** Opposite of [deflate]. Throws [BackupException] when invalid or larger than [maxBytes]. */
    fun inflate(bytes: ByteArray, maxBytes: Int): ByteArray
}
