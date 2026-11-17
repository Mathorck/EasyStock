package ch.mathorck.easystock.services.backup

/** Parsed envelope header. [bytes] is the raw header, authenticated with every chunk. */
class EnvelopeHeader internal constructor(
    internal val bytes: ByteArray,
    internal val kdf: Byte,
    internal val iterations: Int,
    internal val salt: ByteArray,
    internal val noncePrefix: ByteArray,
    internal val chunkSize: Int,
    internal val keyCheck: ByteArray,
) {
    /** True when a user password opens it, false for a raw key (Wi-Fi transfer). */
    val needsPassword: Boolean get() = kdf == KDF_PBKDF2
}
