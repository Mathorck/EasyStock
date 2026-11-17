package ch.mathorck.easystock.e2e

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.db.buildAppDatabase
import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.enums.sync.SyncResult
import ch.mathorck.easystock.services.auth.ServerJoin
import ch.mathorck.easystock.services.server.KtorAccountApi
import ch.mathorck.easystock.services.server.createServerClient
import ch.mathorck.easystock.services.server.serverHttpEngine
import ch.mathorck.easystock.services.sync.AndroidSyncImageFiles
import ch.mathorck.easystock.services.sync.KtorSyncApi
import ch.mathorck.easystock.services.sync.RoomSyncStore
import ch.mathorck.easystock.services.sync.SyncEngine
import ch.mathorck.easystock.services.sync.SyncRemote
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
