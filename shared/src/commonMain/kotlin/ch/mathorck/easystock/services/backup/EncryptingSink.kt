package ch.mathorck.easystock.services.backup

/** Encrypts in chunks. Each chunk is written once the next byte arrives, the last one by [finish]. */
class EncryptingSink internal constructor(
    private val out: ByteSink,
    private val key: ByteArray,
    private val header: ByteArray,
    private val prefix: ByteArray,
    private val chunkSize: Int,
) : ByteSink {
    private val buffer = ByteArray(chunkSize)
    private var size = 0
    private var counter = 0L
    private var finished = false

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        check(!finished)
        var position = offset
        val end = offset + length
        while (position < end) {
            // Full chunk and more data coming: it is not the last one
            if (size == chunkSize) flush(last = false)
            val n = minOf(chunkSize - size, end - position)
            buffer.copyInto(this.buffer, size, position, position + n)
            size += n
            position += n
        }
    }

    /** Writes the last chunk (possibly empty). Nothing can be written after. */
    fun finish() {
        check(!finished)
        flush(last = true)
        finished = true
        key.fill(0)
    }

    private fun flush(last: Boolean) {
        check(counter < 0xFFFFFFFFL) { "Too many chunks" }
        val sealed = BackupCrypto.aesGcmEncrypt(key, chunkNonce(prefix, counter, last), header, buffer.copyOf(size))
        out.write(sealed, 0, sealed.size)
        counter++
        size = 0
    }
}
