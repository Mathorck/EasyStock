package ch.mathorck.easystock.backup

// Platform primitives for backups. Android: JDK (javax.crypto, java.util.zip), also used by the
// JVM host tests. iOS: not implemented (the iOS target is not supported), calls throw.

/** The authentication tag does not match: wrong key, or modified data. */
class CryptoAuthException : Exception("Authentication failed")

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

expect object BackupCompression {
    /** Raw DEFLATE, best compression. */
    fun deflate(bytes: ByteArray): ByteArray

    /** Opposite of [deflate]. Throws [BackupException] when invalid or larger than [maxBytes]. */
    fun inflate(bytes: ByteArray, maxBytes: Int): ByteArray
}

/** Sequential byte input. */
fun interface ByteSource {
    /** Reads up to [length] bytes, returns the count, or -1 at the end. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/** Sequential byte output. */
fun interface ByteSink {
    fun write(buffer: ByteArray, offset: Int, length: Int)
}

/** Reads exactly [length] bytes unless the end comes first. Returns the count read. */
fun ByteSource.readFully(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
    var total = 0
    while (total < length) {
        val n = read(buffer, offset + total, length - total)
        if (n < 0) break
        total += n
    }
    return total
}

fun ByteArray.asByteSource(): ByteSource {
    var position = 0
    return ByteSource { buffer, offset, length ->
        if (position >= size) {
            -1
        } else {
            val n = minOf(length, size - position)
            copyInto(buffer, offset, position, position + n)
            position += n
            n
        }
    }
}

/** Collects written bytes in memory. */
class ByteArraySink : ByteSink {
    private var bytes = ByteArray(1024)
    var size = 0
        private set

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (size + length > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + length))
        buffer.copyInto(bytes, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)
}
