package ch.mathorck.easystock.services.backup

/** Sequential byte input. */
fun interface ByteSource {
    /** Reads up to [length] bytes, returns the count, or -1 at the end. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}
