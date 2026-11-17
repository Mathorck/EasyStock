package ch.mathorck.easystock.services.server

import io.ktor.utils.io.ByteReadChannel

/** `GET /me/data` (right of access): the user's own data as JSON, every role. */
fun interface MyDataApi {
    /** Streams the answer to [write], never held whole in memory. 429 `rate-limited` after 5 per hour. */
    suspend fun downloadMyData(write: suspend (ByteReadChannel) -> Unit)
}
