package ch.mathorck.easystock.services.backup

/** Reads exactly [length] bytes unless the end comes first. Returns the count read. */
fun ByteSource.readFully(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
    var total = 0
    while (total < length) {
        val n = read(buffer, offset + total, length - total)
        if (n < 0) break
        total += n
    }
    return total
}

fun ByteArray.asByteSource(): ByteSource {
    var position = 0
    return ByteSource { buffer, offset, length ->
        if (position >= size) {
            -1
        } else {
            val n = minOf(length, size - position)
            copyInto(buffer, offset, position, position + n)
            position += n
            n
        }
    }
}
