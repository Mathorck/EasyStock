package ch.mathorck.easystock.nfc

/** Longest tag UID kept, in bytes: its hex form fills `article.barcode` and `location.barcode` (varchar(64)). Real UIDs are 4 to 10 bytes. */
const val MAX_TAG_ID_BYTES = 32

private const val HEX = "0123456789ABCDEF"

/**
 * The tag ID stored in `article.barcode` or `location.barcode`: the tag UID (as read by the phone, in that byte order) as
 * uppercase hexadecimal, two digits per byte, no separators. Example: UID 04 a1 b2 c3 d4 e5 f6 -> "04A1B2C3D4E5F6".
 * This is the only place the format is defined: every reader must build the ID with this function.
 * Null when the UID can't identify a tag (empty, or longer than [MAX_TAG_ID_BYTES]).
 */
fun tagIdOf(uid: ByteArray): String? {
    if (uid.isEmpty() || uid.size > MAX_TAG_ID_BYTES) return null
    return buildString(uid.size * 2) {
        for (byte in uid) {
            val value = byte.toInt() and 0xFF
            append(HEX[value shr 4])
            append(HEX[value and 0x0F])
        }
    }
}

/**
 * Normalizes a typed or injected tag ID to the [tagIdOf] format: separators (space, colon, dash) removed,
 * uppercase. Null when it is not an even number of hex digits or too long.
 */
fun normalizeTagId(text: String): String? {
    val hex = text.filterNot { it == ' ' || it == ':' || it == '-' }.uppercase()
    if (hex.isEmpty() || hex.length % 2 != 0 || hex.length > MAX_TAG_ID_BYTES * 2) return null
    return hex.takeIf { id -> id.all { it in HEX } }
}
