package ch.mathorck.easystock.services.backup

// Encrypted envelope around a backup archive or a QR payload.
//
// Header (big-endian):
//   magic "ESBK" (4) | version (1) | kdf (1): 0 raw key, 1 PBKDF2-HMAC-SHA256
//   iterations (4) | salt length (1) | salt | nonce prefix (7) | chunk size (4)
//   key check (16): AES-GCM tag of nothing, nonce = prefix | FFFFFFFF | 02, aad = header before it
// Then the data in chunks of `chunk size` bytes (AES-256-GCM, 16-byte tag each), the last one
// shorter or even empty: nonce = prefix | counter (4) | 1 on the last chunk else 0, aad = whole header.
// The last-chunk flag detects a truncated file, the key check tells a wrong password from a damaged file.

/** Password or raw key used to encrypt a backup. */
sealed interface BackupSecret {
    /** A user password, stretched with PBKDF2. Clear [chars] after use. */
    class Password(val chars: CharArray, val iterations: Int = PBKDF2_ITERATIONS) : BackupSecret

    /** A random 256-bit key (Wi-Fi transfer, carried by the QR code). */
    class Key(val bytes: ByteArray) : BackupSecret {
        init {
            require(bytes.size == KEY_BYTES) { "Key must be $KEY_BYTES bytes" }
        }
    }
}
