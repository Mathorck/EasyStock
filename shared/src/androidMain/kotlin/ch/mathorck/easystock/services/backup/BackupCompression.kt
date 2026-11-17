package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.exceptions.backup.BackupException
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

actual object BackupCompression {
    actual fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size / 2 + 64)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    actual fun inflate(bytes: ByteArray, maxBytes: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw BackupException(BackupError.DAMAGED, "Truncated data")
                }
                if (out.size() + n > maxBytes) throw BackupException(BackupError.TOO_LARGE, "Inflated data too large")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw BackupException(BackupError.DAMAGED, "Invalid compressed data", e)
        } finally {
            inflater.end()
        }
    }
}
