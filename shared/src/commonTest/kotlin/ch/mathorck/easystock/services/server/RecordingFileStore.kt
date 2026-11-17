package ch.mathorck.easystock.services.server

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

internal class RecordingFileStore : ServerFileStore {
    data class Received(val name: String, val mimeType: String, val content: String)

    val received = mutableListOf<Received>()
    var clears = 0
    var receiveError: Exception? = null

    override suspend fun receive(name: String, mimeType: String, channel: ByteReadChannel): LocalFile {
        receiveError?.let { throw it }
        val content = channel.readRemaining().readByteArray().decodeToString()
        received += Received(name, mimeType, content)
        return LocalFile("/cache/server/share/$name", name, content.length.toLong(), mimeType)
    }

    override fun read(file: LocalFile) = ByteReadChannel(byteArrayOf())

    override suspend fun invitePdf(name: String, qrText: String, serverName: String, line: String) = error("unused")

    override suspend fun clear() {
        clears++
    }
}
