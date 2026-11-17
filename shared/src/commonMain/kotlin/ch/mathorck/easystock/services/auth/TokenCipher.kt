package ch.mathorck.easystock.services.auth

/** Encrypts the refresh token with a key the platform keeps out of the app files (Android Keystore). */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws when the data was not made by this key (key lost, file restored from another phone). */
    fun decrypt(data: ByteArray): ByteArray
}
