package ch.mathorck.easystock.services.backup

actual object BackupCrypto {
    actual fun randomBytes(size: Int): ByteArray = unsupported()

    actual fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray = unsupported()

    actual fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray = unsupported()

    actual fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray = unsupported()
}
