package ch.mathorck.easystock.services.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.db.withAppSchema
import ch.mathorck.easystock.data.dto.sync.PullAnswer
import ch.mathorck.easystock.data.model.server.ServerUser
import ch.mathorck.easystock.data.model.sync.PendingPush
import ch.mathorck.easystock.data.model.sync.SyncState
import ch.mathorck.easystock.enums.sync.SyncTable
import ch.mathorck.easystock.services.backup.DeviceFixtures.article
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/** A pulled photo replacing another one: the old file and its hash row go, never a used or pending one. */
class PhotoCleanupDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val files = mutableMapOf<String, ByteArray>()
    private val store by lazy {
        RoomSyncStore(
            db,
            object : SyncImageFiles {
                override suspend fun read(path: String) = files[path]
                override suspend fun exists(path: String) = path in files
                override suspend fun write(path: String, bytes: ByteArray) {
                    files[path] = bytes
                }
                override suspend fun delete(path: String) {
                    files.remove(path)
                }
                override suspend fun deleteAll() = files.clear()
            },
        )
    }
    private val sha1 = "a".repeat(64)
    private val sha2 = "b".repeat(64)

    @Before
    fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).withAppSchema().setDriver(BundledSQLiteDriver()).build()
        assertTrue(db.sessionDao().join(SyncState(serverUrl = "https://nas.example", userId = "u1", role = "editor"), ServerUser("u1", "Ann")))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun pullPhoto(sha: String?) {
        val buffer = PullBuffer().apply {
            add(PullAnswer(rows = mapOf("article" to listOf(toWire(SyncTable.ARTICLE, article("a1"), sha))), next = 1))
        }
        store.applyPull(buffer, 1, false, 5)
    }

    private suspend fun photoPath() = db.articleDao().getById("a1")!!.imagePath!!

    @Test
    fun aPulledReplacementDeletesTheOldFile() = runBlocking {
        pullPhoto(sha1)
        val first = photoPath()
        files[first] = byteArrayOf(1)
        pullPhoto(sha2)
        val second = photoPath()
        files[second] = byteArrayOf(2)

        store.removeOrphanPhotos()

        assertEquals(setOf(second), files.keys)
        assertEquals(listOf(second), db.syncDao().allImageHashes().map { it.imagePath })
    }

    @Test
    fun aPhotoOfThePendingBatchIsKept() = runBlocking {
        pullPhoto(sha1)
        val first = photoPath()
        files[first] = byteArrayOf(1)
        pullPhoto(sha2)
        db.syncDao().savePendingPush(PendingPush(batchId = "b", body = """{"image_sha256":"$sha1"}""", createdAt = 1))

        store.removeOrphanPhotos()

        assertTrue(first in files)
        assertEquals(2, db.syncDao().allImageHashes().size)
    }

    @Test
    fun theSamePhotoPulledAgainIsKept() = runBlocking {
        pullPhoto(sha1)
        val path = photoPath()
        files[path] = byteArrayOf(1)
        pullPhoto(sha1)

        store.removeOrphanPhotos()

        assertEquals(setOf(path), files.keys)
    }
}
