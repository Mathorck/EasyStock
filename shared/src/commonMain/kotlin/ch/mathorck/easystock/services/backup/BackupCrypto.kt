package ch.mathorck.easystock.services.backup

expect object BackupCrypto {
    /** Cryptographically secure random bytes. */
    fun randomBytes(size: Int): ByteArray

    /** PBKDF2-HMAC-SHA256. */
    fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray

    /** AES-GCM with a 128-bit tag. Returns ciphertext followed by the tag. */
    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray

    /** Opposite of [aesGcmEncrypt]. Throws [CryptoAuthException] when the tag does not match. */
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray
}
