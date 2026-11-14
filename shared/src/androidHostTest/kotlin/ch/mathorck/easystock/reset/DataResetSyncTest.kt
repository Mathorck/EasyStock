package ch.mathorck.easystock.reset

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.db.buildAppDatabase
import ch.mathorck.easystock.sync.InMemoryRefusedImageStore
import ch.mathorck.easystock.sync.RefusedImages
import ch.mathorck.easystock.sync.RoomSyncStore
import ch.mathorck.easystock.sync.SyncEngine
import ch.mathorck.easystock.sync.SyncImageFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

/** What the wipe of a logout, a revoked session or a deleted account clears in the sync engine. */
class DataResetSyncTest {
    private val db: AppDatabase = buildAppDatabase(
        // TRUNCATE: no WAL, so Room never asks the (absent) Android context for a service
        Room.inMemoryDatabaseBuilder<AppDatabase>(ContextWrapper(null)).setJournalMode(RoomDatabase.JournalMode.TRUNCATE),
    )
    private val scope = CoroutineScope(SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private object NoImageFiles : SyncImageFiles {
        override suspend fun read(path: String): ByteArray? = null
        override suspend fun exists(path: String) = false
        override suspend fun write(path: String, bytes: ByteArray) {}
        override suspend fun delete(path: String) {}
        override suspend fun deleteAll() {}
    }

    private suspend fun refusedStore() = InMemoryRefusedImageStore().apply {
        save(RefusedImages("http://server:8080", mapOf("e".repeat(64) to null)).encode())
    }

    private fun engine(refused: InMemoryRefusedImageStore) = SyncEngine(
        store = RoomSyncStore(db, NoImageFiles),
        remote = { error("no server call in a wipe") },
        scope = scope,
        refusedImageStore = refused,
    )

    @Test
    fun theWipeClearsTheSavedRefusedPhotos() = runBlocking {
        val refused = refusedStore()
        DataReset(db, wipeFiles = {}, forgetSync = engine(refused)::forgetServer).run()
        assertNull(refused.load())
    }

    @Test
    fun theWipeAfterARevokedSessionClearsTheSavedRefusedPhotos() = runBlocking {
        val refused = refusedStore()
        DataReset(db, wipeFiles = {}, forgetSync = engine(refused)::forgetServer).runAfterRevoke()
        assertNull(refused.load())
    }
}
