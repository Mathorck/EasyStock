package ch.mathorck.easystock.e2e

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import ch.mathorck.easystock.auth.ServerJoin
import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.db.buildAppDatabase
import ch.mathorck.easystock.server.KtorAccountApi
import ch.mathorck.easystock.server.ServerInfo
import ch.mathorck.easystock.server.createServerClient
import ch.mathorck.easystock.server.serverHttpEngine
import ch.mathorck.easystock.sync.AndroidSyncImageFiles
import ch.mathorck.easystock.sync.KtorSyncApi
import ch.mathorck.easystock.sync.PushAnswer
import ch.mathorck.easystock.sync.RoomSyncStore
import ch.mathorck.easystock.sync.SyncApi
import ch.mathorck.easystock.sync.SyncEngine
import ch.mathorck.easystock.sync.SyncRemote
import ch.mathorck.easystock.sync.SyncResult
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * A simulated phone: its own Room database (in memory) and photo folder, joined to the server with
 * [session], syncing with the app's [SyncEngine] over the real HTTP client.
 */
class Phone(val name: String, val session: Session) : AutoCloseable {
    val filesDir: File = Files.createTempDirectory("e2e-$name").toFile()

    val db: AppDatabase = buildAppDatabase(
        // TRUNCATE: no WAL, so Room never asks the (absent) Android context for a service
        Room.inMemoryDatabaseBuilder<AppDatabase>(ContextWrapper(null)).setJournalMode(RoomDatabase.JournalMode.TRUNCATE),
    )

    private val client = createServerClient(E2eServer.connection, serverHttpEngine(null), session.store, E2eServer.versions)

    /** The sync calls, in order: "server-info", "push", "pull". */
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val api = RecordingSyncApi(KtorSyncApi(client), calls)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val engine = SyncEngine(
        store = RoomSyncStore(db, AndroidSyncImageFiles(filesDir)),
        remote = { SyncRemote(api, KtorAccountApi(client)) },
        scope = scope,
    )

    /** Joins the server as the connect screen does after a login. */
    suspend fun join(): Phone {
        assertTrue(ServerJoin(db.sessionDao(), session.store).commit(E2eServer.connection, session.tokenAnswer()), "join of $name")
        return this
    }

    /** One sync cycle, then the photo downloads it started. */
    suspend fun sync(): SyncResult {
        val result = engine.sync()
        engine.awaitDownloads()
        return result
    }

    suspend fun syncOk() {
        val result = sync()
        assertEquals(SyncResult.DONE, result, "sync of $name: ${engine.status.value}, calls ${calls.toList()}")
    }

    suspend fun snapshot(): BackupData = db.backupDao().snapshot()

    suspend fun outboxSize(): Int = db.syncDao().outbox(Int.MAX_VALUE).size

    override fun close() {
        scope.cancel()
        client.close()
        db.close()
        filesDir.deleteRecursively()
    }
}

/** Counts the server calls of the engine, to check what happened before what. */
private class RecordingSyncApi(private val inner: SyncApi, private val calls: MutableList<String>) : SyncApi by inner {
    override suspend fun serverInfo(): ServerInfo = record("server-info") { inner.serverInfo() }

    override suspend fun push(body: String, epoch: String?): PushAnswer = record("push") { inner.push(body, epoch) }

    override suspend fun pull(since: Long, limit: Int, epoch: String?) = record("pull") { inner.pull(since, limit, epoch) }

    /** A failed call is listed with its error: the engine only keeps a [ch.mathorck.easystock.sync.SyncError]. */
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
