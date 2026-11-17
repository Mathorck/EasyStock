package ch.mathorck.easystock.services.server

import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel

/** A streamed backup upload (the file is never fully in memory). */
internal class BackupUpload(
    private val size: Long?,
    private val open: () -> ByteReadChannel,
) : io.ktor.http.content.OutgoingContent.ReadChannelContent() {
    override val contentType: ContentType = ContentType.Application.OctetStream
    override val contentLength: Long? get() = size
    override fun readFrom(): ByteReadChannel = open()
}
