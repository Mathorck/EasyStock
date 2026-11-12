package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.DeviceFixtures.category
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.sync.CONFLICT_REASON
import ch.mathorck.easystock.sync.PullAnswer
import ch.mathorck.easystock.sync.PullBuffer
import ch.mathorck.easystock.sync.PushAnswer
import ch.mathorck.easystock.sync.PushDecision
import ch.mathorck.easystock.sync.PushRequest
import ch.mathorck.easystock.sync.PushResult
import ch.mathorck.easystock.sync.PushStatus
import ch.mathorck.easystock.sync.RowConflict
import ch.mathorck.easystock.sync.RowKeepMineBlock
import ch.mathorck.easystock.sync.SyncTable
import ch.mathorck.easystock.sync.WireFields
import ch.mathorck.easystock.sync.toWire
import ch.mathorck.easystock.sync.withBaseSeq
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `serverSeq` storage and the `conflict` answer (first change wins), on an in-memory database with the outbox triggers. */
class RowConflictDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val dao get() = db.syncDao()
    private lateinit var phoneRow: JsonObject

    @Before
    fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).withAppSchema().setDriver(BundledSQLiteDriver()).build()
        assertTrue(db.sessionDao().join(SyncState(serverUrl = "https://nas.example", userId = "u1", role = "editor"), ServerUser("u1", "Ann")))
        val pulled = JsonObject(toWire(SyncTable.CATEGORY, category("c1", "Bolts")) + (WireFields.SERVER_SEQ to JsonPrimitive(7)))
        val buffer = PullBuffer().apply { add(PullAnswer(rows = mapOf("category" to listOf(pulled)), next = 7)) }
        assertTrue(dao.applyPull(buffer, next = 7, fullPull = true, now = 1))
        assertEquals(7L, dao.serverSeq("category", "c1"))

        // The phone renames it; another phone did first: the server answers `conflict` with its row at seq 9
        db.categoryDao().rename("c1", "Mine", now = 20)
        phoneRow = dao.wireRow(SyncTable.CATEGORY, "c1")!!
        val server = toWire(SyncTable.CATEGORY, category("c1", "Theirs", at = 15))
        val answer = PushAnswer(listOf(PushResult("category", "c1", PushStatus.REJECTED, reason = CONFLICT_REASON, row = server, serverSeq = 9)))
        val decisions = dao.applyPush(PushRequest("b1", mapOf("category" to listOf(phoneRow.withBaseSeq(7)))), answer, now = 50)
        assertTrue(decisions.single() is PushDecision.Conflict)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun name() = (dao.wireRow(SyncTable.CATEGORY, "c1")!!["name"] as JsonPrimitive).content

    private suspend fun changeId() = dao.observeRejectedChanges().first().single().id

    @Test
    fun theServerVersionIsWrittenWithItsSeqAndThePhoneOneKept() = runBlocking {
        assertEquals("Theirs", name())
        assertEquals(9L, dao.serverSeq("category", "c1"))
        assertTrue(dao.outbox(100).isEmpty())
        val change = dao.observeRejectedChanges().first().single()
        assertEquals(RowConflict.Payload(phoneRow, 9), RowConflict.payloadOf(change.reason))
        assertNull(dao.rowKeepMineBlock(change.id, canPush = true))
        assertEquals(RowKeepMineBlock.ROLE, dao.rowKeepMineBlock(change.id, canPush = false))
    }

    @Test
    fun keepTheirsOnlyForgetsTheChoice() = runBlocking {
        assertTrue(dao.keepTheirRow(changeId()))
        assertTrue(dao.observeRejectedChanges().first().isEmpty())
        assertEquals("Theirs", name())
        assertTrue(dao.outbox(100).isEmpty())
    }

    @Test
    fun keepMineQueuesThePhoneVersionOnTheServersSeq() = runBlocking {
        assertNull(dao.keepMyRow(changeId(), canPush = true, now = 99))
        assertTrue(dao.observeRejectedChanges().first().isEmpty())
        assertEquals("Mine", name())
        assertEquals(9L, dao.serverSeq("category", "c1"))
        assertEquals(listOf("category:c1"), dao.outbox(100).map { "${it.tableName}:${it.rowId}" })
        assertEquals(JsonPrimitive(99L), dao.wireRow(SyncTable.CATEGORY, "c1")!!["updatedAt"])
    }

    @Test
    fun keepMineIsRefusedWhenTheNameIsTaken() = runBlocking {
        db.categoryDao().create(category("c2", "Mine"))
        val id = changeId()
        assertEquals(RowKeepMineBlock.VALUE_TAKEN, dao.rowKeepMineBlock(id, canPush = true))
        assertEquals(RowKeepMineBlock.VALUE_TAKEN, dao.keepMyRow(id, canPush = true, now = 99))
        assertEquals("Theirs", name())
        assertEquals(id, changeId())
    }
}
