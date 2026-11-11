package ch.mathorck.easystock.server

import androidx.compose.runtime.Composable
import io.ktor.utils.io.ByteReadChannel

// TODO(server mode on iOS): files and share sheet. Only Android is delivered.
private object UnsupportedServerFiles : ServerFiles {
    override suspend fun receive(name: String, mimeType: String, channel: ByteReadChannel): LocalFile = unsupported()
    override fun read(file: LocalFile): ByteReadChannel = unsupported()
    override suspend fun invitePdf(name: String, qrText: String, serverName: String, line: String): LocalFile = unsupported()
    override suspend fun clear() = Unit
    override fun save(file: LocalFile) = Unit
    override fun share(file: LocalFile) = Unit
    override fun pick() = Unit

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Server mode is not available on iOS yet")
}

@Composable
actual fun rememberServerFiles(onPicked: (LocalFile) -> Unit, onSaved: () -> Unit, onFailed: () -> Unit): ServerFiles =
    UnsupportedServerFiles
