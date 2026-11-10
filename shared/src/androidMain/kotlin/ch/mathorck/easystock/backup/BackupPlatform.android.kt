package ch.mathorck.easystock.backup

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// JDK only (no android.*): also runs in the JVM host tests.

actual object BackupCrypto {
    private val random = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    actual fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBytes * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    actual fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

    actual fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray = try {
        cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(ciphertext)
    } catch (e: AEADBadTagException) {
        throw CryptoAuthException()
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
        }
}

actual object BackupCompression {
    actual fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size / 2 + 64)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    actual fun inflate(bytes: ByteArray, maxBytes: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw BackupException(BackupError.DAMAGED, "Truncated data")
                }
                if (out.size() + n > maxBytes) throw BackupException(BackupError.TOO_LARGE, "Inflated data too large")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw BackupException(BackupError.DAMAGED, "Invalid compressed data", e)
        } finally {
            inflater.end()
        }
    }
}
