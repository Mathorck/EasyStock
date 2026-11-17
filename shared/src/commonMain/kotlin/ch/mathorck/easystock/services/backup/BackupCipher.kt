package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.exceptions.backup.BackupException
import ch.mathorck.easystock.exceptions.backup.CryptoAuthException

/** PBKDF2 iterations for a new backup (OWASP 2023 recommendation for HMAC-SHA256). */
const val PBKDF2_ITERATIONS = 600_000

const val KEY_BYTES = 32

private val MAGIC = byteArrayOf('E'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())

private const val ENVELOPE_VERSION: Byte = 1

private const val KDF_RAW: Byte = 0

internal const val KDF_PBKDF2: Byte = 1

private const val SALT_BYTES = 16

private const val NONCE_PREFIX_BYTES = 7

private const val TAG_BYTES = 16

private const val DEFAULT_CHUNK = 64 * 1024

// Bounds accepted when reading an untrusted header
private const val MIN_ITERATIONS = 1_000

private const val MAX_ITERATIONS = 10_000_000

private const val MIN_CHUNK = 1024

private const val MAX_CHUNK = 1024 * 1024

object BackupCipher {
    /** True when [prefix] (first bytes of a file or payload) starts an encrypted envelope. */
    fun isEncrypted(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC.size && MAGIC.indices.all { prefix[it] == MAGIC[it] }

    /** Encrypts [source] to [sink] (whole stream, memory use bounded by the chunk size). */
    fun encrypt(source: ByteSource, sink: ByteSink, secret: BackupSecret, chunkSize: Int = DEFAULT_CHUNK) {
        val writer = encryptingSink(sink, secret, chunkSize)
        val buffer = ByteArray(chunkSize)
        while (true) {
            val n = source.read(buffer, 0, buffer.size)
            if (n < 0) break
            writer.write(buffer, 0, n)
        }
        writer.finish()
    }

    /** A sink that encrypts what is written to it. Call [EncryptingSink.finish] at the end. */
    fun encryptingSink(sink: ByteSink, secret: BackupSecret, chunkSize: Int = DEFAULT_CHUNK): EncryptingSink {
        require(chunkSize in MIN_CHUNK..MAX_CHUNK)
        val prefix = BackupCrypto.randomBytes(NONCE_PREFIX_BYTES)
        val (kdf, iterations, salt) = when (secret) {
            is BackupSecret.Password -> Triple(KDF_PBKDF2, secret.iterations, BackupCrypto.randomBytes(SALT_BYTES))
            is BackupSecret.Key -> Triple(KDF_RAW, 0, ByteArray(0))
        }
        val key = deriveKey(kdf, iterations, salt, secret)
        val start = buildList {
            addAll(MAGIC.toList())
            add(ENVELOPE_VERSION)
            add(kdf)
            addAll(int(iterations).toList())
            add(salt.size.toByte())
            addAll(salt.toList())
            addAll(prefix.toList())
            addAll(int(chunkSize).toList())
        }.toByteArray()
        val keyCheck = BackupCrypto.aesGcmEncrypt(key, keyCheckNonce(prefix), start, ByteArray(0))
        val header = start + keyCheck
        sink.write(header, 0, header.size)
        return EncryptingSink(sink, key, header, prefix, chunkSize)
    }

    /**
     * Reads the header from [source]. Throws [BackupException]: [BackupError.NOT_A_BACKUP] without the magic,
     * [BackupError.NEWER_VERSION] for a newer envelope, [BackupError.DAMAGED] for invalid values.
     */
    fun readHeader(source: ByteSource): EnvelopeHeader {
        val bytes = ArrayList<Byte>(64)
        fun take(count: Int): ByteArray {
            val buffer = ByteArray(count)
            if (source.readFully(buffer) != count) throw BackupException(BackupError.DAMAGED, "Truncated header")
            bytes.addAll(buffer.toList())
            return buffer
        }
        if (!isEncrypted(take(MAGIC.size))) throw BackupException(BackupError.NOT_A_BACKUP, "No envelope")
        val version = take(1)[0]
        if (version > ENVELOPE_VERSION) throw BackupException(BackupError.NEWER_VERSION, "Envelope $version")
        if (version < 1) throw BackupException(BackupError.DAMAGED, "Envelope version")
        val kdf = take(1)[0]
        val iterations = int(take(4))
        val saltSize = take(1)[0].toInt() and 0xFF
        val salt = take(saltSize)
        val prefix = take(NONCE_PREFIX_BYTES)
        val chunkSize = int(take(4))
        when (kdf) {
            KDF_RAW -> if (iterations != 0 || saltSize != 0) throw BackupException(BackupError.DAMAGED, "Raw key header")
            KDF_PBKDF2 -> if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS || saltSize < SALT_BYTES) {
                throw BackupException(BackupError.DAMAGED, "Key derivation")
            }
            else -> throw BackupException(BackupError.NEWER_VERSION, "Key derivation $kdf")
        }
        if (chunkSize !in MIN_CHUNK..MAX_CHUNK) throw BackupException(BackupError.DAMAGED, "Chunk size")
        val start = bytes.toByteArray()
        val keyCheck = take(TAG_BYTES)
        return EnvelopeHeader(start + keyCheck, kdf, iterations, salt, prefix, chunkSize, keyCheck)
    }

    /**
     * The key that opens [header]. Throws [BackupException]: [BackupError.PASSWORD_REQUIRED] when the
     * secret kind doesn't match, [BackupError.WRONG_PASSWORD] when the key check fails.
     */
    fun unlock(header: EnvelopeHeader, secret: BackupSecret): ByteArray {
        val matches = when (secret) {
            is BackupSecret.Password -> header.kdf == KDF_PBKDF2
            is BackupSecret.Key -> header.kdf == KDF_RAW
        }
        if (!matches) throw BackupException(BackupError.PASSWORD_REQUIRED, "Other secret kind")
        val key = deriveKey(header.kdf, header.iterations, header.salt, secret)
        val start = header.bytes.copyOf(header.bytes.size - TAG_BYTES)
        try {
            BackupCrypto.aesGcmDecrypt(key, keyCheckNonce(header.noncePrefix), start, header.keyCheck)
        } catch (e: CryptoAuthException) {
            throw BackupException(BackupError.WRONG_PASSWORD, "Key check failed", e)
        }
        return key
    }

    /**
     * Decrypts the chunks after the header to [sink]. Throws [BackupException] ([BackupError.DAMAGED])
     * on a modified or truncated stream. [sink] may already have received data then: write to a
     * temporary place and only use it once this returns.
     */
    fun decrypt(header: EnvelopeHeader, key: ByteArray, source: ByteSource, sink: ByteSink) {
        val blockSize = header.chunkSize + TAG_BYTES
        var current = ByteArray(blockSize)
        var next = ByteArray(blockSize)
        var currentSize = source.readFully(current)
        if (currentSize < TAG_BYTES) throw BackupException(BackupError.DAMAGED, "Truncated")
        var counter = 0L
        while (true) {
            val nextSize = source.readFully(next)
            val last = nextSize == 0
            if (!last && (currentSize != blockSize || nextSize < TAG_BYTES)) throw BackupException(BackupError.DAMAGED, "Truncated chunk")
            if (counter >= 0xFFFFFFFFL) throw BackupException(BackupError.TOO_LARGE, "Too many chunks")
            val plain = try {
                BackupCrypto.aesGcmDecrypt(key, chunkNonce(header.noncePrefix, counter, last), header.bytes, current.copyOf(currentSize))
            } catch (e: CryptoAuthException) {
                throw BackupException(BackupError.DAMAGED, "Chunk $counter", e)
            }
            sink.write(plain, 0, plain.size)
            if (last) return
            val swap = current
            current = next
            next = swap
            currentSize = nextSize
            counter++
        }
    }

    /** Encrypts in memory (QR payload). */
    fun encrypt(plain: ByteArray, secret: BackupSecret, chunkSize: Int = DEFAULT_CHUNK): ByteArray {
        val out = ByteArraySink()
        encrypt(plain.asByteSource(), out, secret, chunkSize)
        return out.toByteArray()
    }

    /** Decrypts in memory (QR payload). Same errors as [readHeader], [unlock] and [decrypt]. */
    fun decrypt(encrypted: ByteArray, secret: BackupSecret): ByteArray {
        val source = encrypted.asByteSource()
        val header = readHeader(source)
        val key = unlock(header, secret)
        val out = ByteArraySink()
        decrypt(header, key, source, out)
        return out.toByteArray()
    }

    private fun deriveKey(kdf: Byte, iterations: Int, salt: ByteArray, secret: BackupSecret): ByteArray = when (secret) {
        is BackupSecret.Key -> secret.bytes.copyOf()
        is BackupSecret.Password -> {
            check(kdf == KDF_PBKDF2)
            BackupCrypto.pbkdf2Sha256(secret.chars, salt, iterations, KEY_BYTES)
        }
    }
}

internal fun chunkNonce(prefix: ByteArray, counter: Long, last: Boolean): ByteArray =
    prefix + int(counter.toInt()) + byteArrayOf(if (last) 1 else 0)

private fun keyCheckNonce(prefix: ByteArray): ByteArray = prefix + int(-1) + byteArrayOf(2)

private fun int(value: Int): ByteArray =
    byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

private fun int(bytes: ByteArray): Int =
    ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
