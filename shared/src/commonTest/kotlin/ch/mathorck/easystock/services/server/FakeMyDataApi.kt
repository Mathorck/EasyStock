package ch.mathorck.easystock.services.server

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred

internal class FakeMyDataApi : MyDataApi {
    var body = """{"formatVersion":1}"""
    var error: Exception? = null
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun downloadMyData(write: suspend (ByteReadChannel) -> Unit) {
        calls++
        gate?.await()
        error?.let { throw it }
        write(ByteReadChannel(body))
    }
}
