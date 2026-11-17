package ch.mathorck.easystock.services.backup

/** Sequential byte output. */
fun interface ByteSink {
    fun write(buffer: ByteArray, offset: Int, length: Int)
}
