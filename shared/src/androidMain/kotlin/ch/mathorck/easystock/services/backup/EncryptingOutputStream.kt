package ch.mathorck.easystock.services.backup

import java.io.OutputStream

/** An output stream encrypting into [sink]. [close] writes the last chunk, the underlying stream stays open. */
class EncryptingOutputStream(private val sink: EncryptingSink) : OutputStream() {
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) = sink.write(b, off, len)

    override fun close() {
        if (closed) return
        closed = true
        sink.finish()
    }
}
