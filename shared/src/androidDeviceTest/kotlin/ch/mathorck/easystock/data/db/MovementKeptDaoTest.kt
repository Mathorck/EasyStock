package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.DeviceFixtures.article
import ch.mathorck.easystock.backup.DeviceFixtures.location
import ch.mathorck.easystock.backup.DeviceFixtures.movement
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.sync.ConflictCodes
import ch.mathorck.easystock.sync.KeepMineBlock
import ch.mathorck.easystock.sync.MovementKept
import ch.mathorck.easystock.sync.PullAnswer
import ch.mathorck.easystock.sync.PullBuffer
import ch.mathorck.easystock.sync.PushAnswer
import ch.mathorck.easystock.sync.PushDecision
import ch.mathorck.easystock.sync.PushRequest
import ch.mathorck.easystock.sync.PushResult
import ch.mathorck.easystock.sync.PushStatus
import ch.mathorck.easystock.sync.SyncTable
import ch.mathorck.easystock.sync.toWire
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `MOVEMENT_KEPT` answers and the two choices, on an in-memory database with the outbox triggers. */
class MovementKeptDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val dao get() = db.syncDao()
    private lateinit var phoneRow: JsonObject

    @Before
    fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).withAppSchema().setDriver(BundledSQLiteDriver()).build()
        assertTrue(db.sessionDao().join(SyncState(serverUrl = "https://nas.example", userId = "u1", role = "editor"), ServerUser("u1", "Ann")))
        val buffer = PullBuffer().apply {
            add(
                PullAnswer(
                    rows = mapOf(
                        "location" to listOf(toWire(SyncTable.LOCATION, location("l1"))),
                        "article" to listOf(toWire(SyncTable.ARTICLE, article("a1"))),
                    ),
                    next = 42,
                ),
            )
        }
        assertTrue(dao.applyPull(buffer, next = 42, fullPull = true, now = 1))
        // The phone's movement: +3, never accepted by the server, which has +2 under the same id
        db.stockDao().insertMovement(movement("m1", "a1", "l1", 3000, at = 5))
        phoneRow = dao.wireRow(SyncTable.STOCK_MOVEMENT, "m1")!!
        val answer = PushAnswer(
            listOf(
                PushResult(
                    "stock_movement",
                    "m1",
                    PushStatus.CHANGED,
                    conflict = ConflictCodes.MOVEMENT_KEPT,
                    row = buildJsonObject {
                        put("id", "m1")
                        put("quantity", 2000)
                        put("createdBy", "u2")
                    },
                ),
            ),
        )
        val decisions = dao.applyPush(PushRequest("b1", mapOf("stock_movement" to listOf(phoneRow))), answer, now = 50)
        assertTrue(decisions.single() is PushDecision.MovementKept)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun stock() = db.stockActionDao().stockAt("a1", "l1")

    private suspend fun quantityOf(id: String) = (dao.wireRow(SyncTable.STOCK_MOVEMENT, id)!!["quantity"] as JsonPrimitive).content.toLong()

    private suspend fun changeId() = dao.observeRejectedChanges().first().single().id

    @Test
    fun theServerVersionIsShownAndThePhoneOneKept() = runBlocking {
        assertEquals(2000, quantityOf("m1"))
        assertEquals(2000, stock())
        // Written with `applying` on: nothing to push
        assertTrue(dao.outbox(100).isEmpty())
        assertEquals(false, dao.syncState()!!.applying)
        val change = dao.observeRejectedChangeRows().first().single()
        assertEquals("m1", change.change.rowId)
        assertEquals("a1", change.label)
        assertEquals(phoneRow, MovementKept.phoneRowOf(change.change.reason))
        assertEquals(1, dao.observeUnseenRejectedCount().first())

        val details = assertNotNull(dao.movementKeptDetails(change.change.id, canStockAction = true))
        assertEquals(3000, details.phone.quantityMilli)
        assertEquals(2000, details.server!!.quantityMilli)
        assertNull(details.keepMineBlock)
        assertEquals(KeepMineBlock.ROLE, dao.movementKeptDetails(change.change.id, canStockAction = false)!!.keepMineBlock)
    }

    @Test
    fun useTheServerVersionOnlyForgetsTheChoice() = runBlocking {
        assertTrue(dao.useServerMovement(changeId()))
        assertTrue(dao.observeRejectedChanges().first().isEmpty())
        assertEquals(2000, quantityOf("m1"))
        assertEquals(2000, stock())
        assertTrue(dao.outbox(100).isEmpty())
    }

    @Test
    fun keepMineAddsTheQuantityAsANewMovement() = runBlocking {
        assertNull(dao.keepMyMovement(changeId(), "m2", canStockAction = true, now = 99))
        assertTrue(dao.observeRejectedChanges().first().isEmpty())
        // The server's movement is untouched, the new one is queued
        assertEquals(2000, quantityOf("m1"))
        assertEquals(3000, quantityOf("m2"))
        assertEquals(5000, stock())
        assertEquals(listOf("stock_movement:m2"), dao.outbox(100).map { "${it.tableName}:${it.rowId}" })
        val m2 = dao.wireRow(SyncTable.STOCK_MOVEMENT, "m2")!!
        assertEquals(JsonPrimitive(99L), m2["occurredAt"])
        assertEquals(JsonPrimitive(99L), m2["createdAt"])
        assertEquals(JsonPrimitive(MovementReason.PURCHASE.name.lowercase()), m2["reason"])
    }

    @Test
    fun keepMineIsRefusedWithoutTheRole() = runBlocking {
        val id = changeId()
        assertEquals(KeepMineBlock.ROLE, dao.keepMyMovement(id, "m2", canStockAction = false, now = 99))
        assertEquals(id, changeId())
        assertEquals(2000, stock())
        assertTrue(dao.outbox(100).isEmpty())
    }
}
