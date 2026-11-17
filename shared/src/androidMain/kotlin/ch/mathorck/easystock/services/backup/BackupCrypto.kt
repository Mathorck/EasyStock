package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.exceptions.backup.CryptoAuthException
import java.security.SecureRandom
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
