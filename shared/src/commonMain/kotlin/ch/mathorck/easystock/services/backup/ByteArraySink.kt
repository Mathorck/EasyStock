package ch.mathorck.easystock.services.backup

/** Collects written bytes in memory. */
class ByteArraySink : ByteSink {
    private var bytes = ByteArray(1024)
    var size = 0
        private set

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (size + length > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + length))
        buffer.copyInto(bytes, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)
}
