package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
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
import ch.mathorck.easystock.sync.MovementDiffers
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A server movement that differs from the phone's (`MOVEMENT_KEPT`, a changed or refused row sent back),
 * and the choices of the `movement-kept` entries older app versions left, on an in-memory database with
 * the outbox triggers. Movements are append-only: the phone never writes the server's values over its own.
 */
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
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun stock() = db.stockActionDao().stockAt("a1", "l1")

    private suspend fun quantityOf(id: String) = (dao.wireRow(SyncTable.STOCK_MOVEMENT, id)!!["quantity"] as JsonPrimitive).content.toLong()

    private suspend fun stored(id: String) = db.backupDao().snapshot().movements.single { it.id == id }

    private suspend fun changeId() = dao.observeRejectedChanges().first().single().id

    private suspend fun push(result: PushResult): PushDecision =
        dao.applyPush(PushRequest("b1", mapOf("stock_movement" to listOf(phoneRow))), PushAnswer(listOf(result)), now = 50).single()

    private fun serverRow(quantity: Long, by: String? = "u2") = buildJsonObject {
        put("id", "m1")
        put("quantity", quantity)
        put("createdBy", by)
    }

    @Test
    fun aDifferingServerMovementNeverChangesTheLocalOneAndIsReported() = runBlocking {
        val decision = push(PushResult("stock_movement", "m1", PushStatus.CHANGED, conflict = ConflictCodes.MOVEMENT_KEPT, row = serverRow(2000)))
        assertIs<PushDecision.MovementKept>(decision)
        // Append-only: quantity and every other column stay the phone's, only the author is the server's
        assertEquals(movement("m1", "a1", "l1", 3000, at = 5).copy(createdBy = "u2"), stored("m1"))
        assertEquals(3000, stock())
        // Nothing to push, and `applying` is off again
        assertTrue(dao.outbox(100).isEmpty())
        assertEquals(false, dao.syncState()!!.applying)
        // Reported in "Changes not accepted", with both versions
        val change = dao.observeRejectedChangeRows().first().single()
        assertEquals("m1", change.change.rowId)
        assertEquals("a1", change.label)
        assertEquals(1, dao.observeUnseenRejectedCount().first())
        val versions = assertNotNull(MovementDiffers.versionsOf(change.change.reason))
        assertEquals(3000, versions.phone.quantityMilli)
        assertEquals(2000, versions.server.quantityMilli)
        // Not an entry of the old kind: no "Keep mine", which would count the phone's quantity twice
        assertNull(MovementKept.phoneRowOf(change.change.reason))
        assertNull(dao.movementKeptDetails(change.change.id, canStockAction = true))
        assertEquals(KeepMineBlock.INVALID, dao.keepMyMovement(change.change.id, "m2", canStockAction = true, now = 99))
        assertEquals(3000, stock())
    }

    @Test
    fun theSameServerMovementOnlySetsTheAuthor() = runBlocking {
        val same = JsonObject(phoneRow + mapOf("createdBy" to JsonPrimitive("u2"), "deviceId" to JsonPrimitive("d2")))
        push(PushResult("stock_movement", "m1", PushStatus.CHANGED, conflict = ConflictCodes.MOVEMENT_KEPT, row = same))
        assertEquals(movement("m1", "a1", "l1", 3000, at = 5).copy(createdBy = "u2", deviceId = "d2"), stored("m1"))
        assertTrue(dao.observeRejectedChanges().first().isEmpty())
        assertTrue(dao.outbox(100).isEmpty())
    }

    @Test
    fun aChangedMovementAnswerNeverOverwritesTheLocalOne() = runBlocking {
        val decision = push(PushResult("stock_movement", "m1", PushStatus.CHANGED, row = serverRow(2000)))
        assertIs<PushDecision.Write>(decision)
        assertEquals(3000, quantityOf("m1"))
        assertEquals("u2", stored("m1").createdBy)
        assertEquals(3000, stock())
        val versions = assertNotNull(MovementDiffers.versionsOf(dao.observeRejectedChanges().first().single().reason))
        assertEquals(2000, versions.server.quantityMilli)
        assertTrue(dao.outbox(100).isEmpty())
    }

    @Test
    fun aRefusedMovementWithTheServerRowKeepsTheLocalOneAndIsListedOnce() = runBlocking {
        push(PushResult("stock_movement", "m1", PushStatus.REJECTED, reason = "not-allowed", row = serverRow(2000)))
        assertEquals(3000, quantityOf("m1"))
        assertEquals("not-allowed", dao.observeRejectedChanges().first().single().reason)
    }

    // ---------- `movement-kept` entries of an older app version (the local movement is the server's) ----------

    /** What an older version left: the server's +2 written over the movement, the phone's +3 in the entry. */
    private suspend fun legacyEntry() {
        db.stockDao().insertMovement(movement("m5", "a1", "l1", 2000, at = 5))
        val phone = toWire(SyncTable.STOCK_MOVEMENT, movement("m5", "a1", "l1", 3000, at = 5))
        db.useWriterConnection {
            it.execSQL(
                "INSERT INTO rejected_change (id, table_name, row_id, reason, rejected_at) " +
                    "VALUES ('r1', 'stock_movement', 'm5', '${MovementKept.reasonOf(phone).replace("'", "''")}', 50)",
            )
        }
    }

    @Test
    fun aLegacyEntryShowsBothVersions() = runBlocking {
        legacyEntry()
        val details = assertNotNull(dao.movementKeptDetails("r1", canStockAction = true))
        assertEquals(3000, details.phone.quantityMilli)
        assertEquals(2000, details.server!!.quantityMilli)
        assertNull(details.keepMineBlock)
        assertEquals(KeepMineBlock.ROLE, dao.movementKeptDetails("r1", canStockAction = false)!!.keepMineBlock)
    }

    @Test
    fun useTheServerVersionOnlyForgetsTheChoice() = runBlocking {
        legacyEntry()
        val queuedBefore = dao.outbox(100)
        assertTrue(dao.useServerMovement(changeId()))
        assertTrue(dao.observeRejectedChanges().first().isEmpty())
        assertEquals(2000, quantityOf("m5"))
        assertEquals(5000, stock())
        assertEquals(queuedBefore, dao.outbox(100))
    }

    @Test
    fun keepMineAddsTheQuantityAsANewMovement() = runBlocking {
        legacyEntry()
        val queuedBefore = dao.outbox(100).map { "${it.tableName}:${it.rowId}" }
        assertNull(dao.keepMyMovement(changeId(), "m2", canStockAction = true, now = 99))
        assertTrue(dao.observeRejectedChanges().first().isEmpty())
        // The server's movement is untouched, the new one is queued
        assertEquals(2000, quantityOf("m5"))
        assertEquals(3000, quantityOf("m2"))
        assertEquals(queuedBefore + "stock_movement:m2", dao.outbox(100).map { "${it.tableName}:${it.rowId}" })
        val m2 = dao.wireRow(SyncTable.STOCK_MOVEMENT, "m2")!!
        assertEquals(JsonPrimitive(99L), m2["occurredAt"])
        assertEquals(JsonPrimitive(99L), m2["createdAt"])
        assertEquals(JsonPrimitive(MovementReason.PURCHASE.name.lowercase()), m2["reason"])
    }

    @Test
    fun keepMineIsRefusedWithoutTheRole() = runBlocking {
        legacyEntry()
        val id = changeId()
        assertEquals(KeepMineBlock.ROLE, dao.keepMyMovement(id, "m2", canStockAction = false, now = 99))
        assertEquals(id, changeId())
        assertNull(dao.wireRow(SyncTable.STOCK_MOVEMENT, "m2"))
    }
}
