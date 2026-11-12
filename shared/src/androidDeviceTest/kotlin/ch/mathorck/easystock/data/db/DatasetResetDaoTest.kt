package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.DeviceFixtures.article
import ch.mathorck.easystock.backup.DeviceFixtures.category
import ch.mathorck.easystock.backup.DeviceFixtures.location
import ch.mathorck.easystock.backup.DeviceFixtures.movement
import ch.mathorck.easystock.data.model.ImageHash
import ch.mathorck.easystock.data.model.PendingPush
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.sync.PullAnswer
import ch.mathorck.easystock.sync.PullBuffer
import ch.mathorck.easystock.sync.RoomSyncStore
import ch.mathorck.easystock.sync.SyncImageFiles
import ch.mathorck.easystock.sync.SyncTable
import ch.mathorck.easystock.sync.toWire
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The wipe of a `dataset-reset` and the full pull after it, on an in-memory database with the outbox triggers. */
class DatasetResetDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val dao get() = db.syncDao()

    private val files = object : SyncImageFiles {
        val stored = mutableMapOf<String, ByteArray>()
        override suspend fun read(path: String) = stored[path]
        override suspend fun exists(path: String) = path in stored
        override suspend fun write(path: String, bytes: ByteArray) {
            stored[path] = bytes
        }
        override suspend fun delete(path: String) {
            stored.remove(path)
        }
        override suspend fun deleteAll() = stored.clear()
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).withAppSchema().setDriver(BundledSQLiteDriver()).build()
        assertTrue(db.sessionDao().join(SyncState(serverUrl = "https://nas.example", userId = "u1", role = "editor"), ServerUser("u1", "Ann")))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun theWipeKeepsTheSessionAndThePullAfterItQueuesNothing() = runBlocking {
        // Pulled rows of the old data set, then a local change waiting
        val old = PullBuffer().apply {
            add(
                PullAnswer(
                    rows = mapOf(
                        "category" to listOf(toWire(SyncTable.CATEGORY, category("c1"))),
                        "location" to listOf(toWire(SyncTable.LOCATION, location("l1"))),
                        "article" to listOf(toWire(SyncTable.ARTICLE, article("a1", category = "c1"))),
                        "stock_movement" to listOf(toWire(SyncTable.STOCK_MOVEMENT, movement("m1", "a1", "l1", 1000))),
                    ),
                    next = 42,
                ),
            )
        }
        assertTrue(dao.applyPull(old, next = 42, fullPull = true, now = 10))
        db.articleDao().insert(article("a2", sku = "LOCAL"))
        dao.savePendingPush(PendingPush(batchId = "b1", body = "{}", createdAt = 11))
        dao.saveImageHash(ImageHash("images/00000000-0000-0000-0000-000000000001.jpg", "ab"))
        files.stored["images/00000000-0000-0000-0000-000000000001.jpg"] = byteArrayOf(1)
        assertEquals(1, dao.outbox(100).size)

        val store = RoomSyncStore(db, files)
        assertTrue(store.wipeServerData())

        val state = assertNotNull(dao.syncState())
        assertEquals(0, state.since)
        assertFalse(state.applying)
        assertEquals("editor", state.role)
        assertTrue(dao.outbox(100).isEmpty())
        assertNull(dao.pendingPush())
        assertNull(dao.hashOf("images/00000000-0000-0000-0000-000000000001.jpg"))
        assertTrue(files.stored.isEmpty())
        assertTrue(dao.existingIds(SyncTable.ARTICLE, setOf("a1", "a2")).isEmpty())
        assertTrue(dao.existingIds(SyncTable.STOCK_MOVEMENT, setOf("m1")).isEmpty())
        assertTrue(dao.existingIds(SyncTable.CATEGORY, setOf("c1")).isEmpty())
        assertEquals(0, dao.observeUnseenRejectedCount().first())

        // The new data set, pulled from 0: written without filling the outbox
        val fresh = PullBuffer().apply {
            add(PullAnswer(rows = mapOf("category" to listOf(toWire(SyncTable.CATEGORY, category("c9")))), next = 7))
        }
        assertTrue(dao.applyPull(fresh, next = 7, fullPull = true, now = 20))
        assertEquals(setOf("c9"), dao.existingIds(SyncTable.CATEGORY, setOf("c1", "c9")))
        assertTrue(dao.outbox(100).isEmpty())
        assertEquals(7, dao.syncState()!!.since)

        // Local changes are queued again after the wipe
        db.articleDao().insert(article("a3", category = "c9"))
        assertEquals(listOf("a3"), dao.outbox(100).map { it.rowId })
    }

    @Test
    fun nothingIsWipedWhenNotConnected() = runBlocking {
        db.backupDao().wipeEverything()
        db.articleDao().insert(article("a1"))
        assertFalse(RoomSyncStore(db, files).wipeServerData())
        assertEquals(setOf("a1"), dao.existingIds(SyncTable.ARTICLE, setOf("a1")))
    }
}
