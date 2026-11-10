package ch.mathorck.easystock.backup

// The iOS target is not supported: backups are Android only. Compiles, throws when used.

private fun unsupported(): Nothing = throw UnsupportedOperationException("Backups are not available on iOS")

actual object BackupCrypto {
    actual fun randomBytes(size: Int): ByteArray = unsupported()

    actual fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray = unsupported()

    actual fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray = unsupported()

    actual fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray = unsupported()
}

actual object BackupCompression {
    actual fun deflate(bytes: ByteArray): ByteArray = unsupported()

    actual fun inflate(bytes: ByteArray, maxBytes: Int): ByteArray = unsupported()
}

@androidx.compose.runtime.Composable
actual fun rememberBackupService(): BackupService = unsupported()
