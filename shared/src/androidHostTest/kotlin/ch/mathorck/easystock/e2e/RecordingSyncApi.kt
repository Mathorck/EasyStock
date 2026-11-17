package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.data.dto.sync.PushAnswer
import ch.mathorck.easystock.services.sync.SyncApi

/** Counts the server calls of the engine, to check what happened before what. */
internal class RecordingSyncApi(private val inner: SyncApi, private val calls: MutableList<String>) : SyncApi by inner {
    override suspend fun serverInfo(): ServerInfo = record("server-info") { inner.serverInfo() }

    override suspend fun push(body: String, epoch: String?): PushAnswer = record("push") { inner.push(body, epoch) }

    override suspend fun pull(since: Long, limit: Int, epoch: String?) = record("pull") { inner.pull(since, limit, epoch) }

    /** A failed call is listed with its error: the engine only keeps a [ch.mathorck.easystock.enums.sync.SyncError]. */
    private inline fun <T> record(name: String, call: () -> T): T {
        calls += name
        return try {
            call()
        } catch (e: Exception) {
            calls += "$name failed: $e"
            throw e
        }
    }
}
