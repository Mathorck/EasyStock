package ch.mathorck.easystock.backup

/**
 * Base45 (RFC 9285). Uses only the QR alphanumeric alphabet, so a QR code stores it in
 * alphanumeric mode (5.5 bits per char): denser than Base64 in byte mode.
 * The alphabet includes a space: never trim a scanned text.
 */
object Base45 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    private val DECODE = IntArray(128) { -1 }.also { table -> ALPHABET.forEachIndexed { i, c -> table[c.code] = i } }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 1) / 2 * 3)
        var i = 0
        while (i + 1 < bytes.size) {
            var n = (bytes[i].toInt() and 0xFF) * 256 + (bytes[i + 1].toInt() and 0xFF)
            out.append(ALPHABET[n % 45]); n /= 45
            out.append(ALPHABET[n % 45]); n /= 45
            out.append(ALPHABET[n])
            i += 2
        }
        if (i < bytes.size) {
            val n = bytes[i].toInt() and 0xFF
            out.append(ALPHABET[n % 45]).append(ALPHABET[n / 45])
        }
        return out.toString()
    }

    /** Throws [IllegalArgumentException] on an invalid text. */
    fun decode(text: String): ByteArray {
        require(text.length % 3 != 1) { "Invalid Base45 length" }
        val out = ByteArray(text.length / 3 * 2 + if (text.length % 3 == 2) 1 else 0)
        fun digit(c: Char): Int {
            val d = if (c.code < 128) DECODE[c.code] else -1
            require(d >= 0) { "Invalid Base45 character" }
            return d
        }
        var o = 0
        var i = 0
        while (i + 2 < text.length) {
            val n = digit(text[i]) + digit(text[i + 1]) * 45 + digit(text[i + 2]) * 45 * 45
            require(n <= 0xFFFF) { "Invalid Base45 group" }
            out[o++] = (n shr 8).toByte()
            out[o++] = n.toByte()
            i += 3
        }
        if (i < text.length) {
            val n = digit(text[i]) + digit(text[i + 1]) * 45
            require(n <= 0xFF) { "Invalid Base45 group" }
            out[o] = n.toByte()
        }
        return out
    }

    /** Length of [encode] for [byteCount] bytes. */
    fun encodedLength(byteCount: Int): Int = byteCount / 2 * 3 + if (byteCount % 2 == 1) 2 else 0
}
