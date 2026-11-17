package ch.mathorck.easystock.services.server

import io.ktor.utils.io.ByteReadChannel

internal class FakeFileStore : ServerFileStore {
    override suspend fun receive(name: String, mimeType: String, channel: ByteReadChannel) = LocalFile("/c/$name", name, 3, mimeType)
    override fun read(file: LocalFile) = ByteReadChannel(byteArrayOf(1))
    override suspend fun invitePdf(name: String, qrText: String, serverName: String, line: String) = LocalFile("/c/$name", name, 1, "application/pdf")
    var clears = 0
    override suspend fun clear() {
        clears++
    }
}
