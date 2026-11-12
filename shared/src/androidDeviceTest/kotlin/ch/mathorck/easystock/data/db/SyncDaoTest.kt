package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.backup.DeviceFixtures.article
import ch.mathorck.easystock.backup.DeviceFixtures.category
import ch.mathorck.easystock.backup.DeviceFixtures.item
import ch.mathorck.easystock.backup.DeviceFixtures.location
import ch.mathorck.easystock.backup.DeviceFixtures.movement
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.data.model.Tracking
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sync writes on an in-memory database with the outbox triggers, never the app one. */
class SyncDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val dao get() = db.syncDao()

    @Before
    fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).withAppSchema().setDriver(BundledSQLiteDriver()).build()
        assertTrue(db.sessionDao().join(SyncState(serverUrl = "https://nas.example", userId = "u1", role = "editor"), ServerUser("u1", "Ann")))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pull(vararg rows: Pair<SyncTable, JsonObject>) = PullBuffer().apply {
        add(PullAnswer(rows = rows.groupBy({ it.first.tableName }, { it.second }), next = 42))
    }

    @Test
    fun aLocalChangeIsQueuedOnce() = runBlocking {
        db.articleDao().insert(article("a1"))
        db.articleDao().insert(article("a2"))
        assertEquals(listOf("a1", "a2"), dao.outbox(100).map { it.rowId }.sorted())
        assertEquals(2, dao.observeOutboxCount().first())
    }

    @Test
    fun pulledRowsAreNotQueuedAndMoveTheCursor() = runBlocking {
        val buffer = pull(
            SyncTable.CATEGORY to toWire(SyncTable.CATEGORY, category("c1")),
            SyncTable.LOCATION to toWire(SyncTable.LOCATION, location("l1")),
            SyncTable.ARTICLE to toWire(SyncTable.ARTICLE, article("a1", category = "c1", tracking = Tracking.SERIAL)),
            SyncTable.ITEM to toWire(SyncTable.ITEM, item("i1", "a1")),
            SyncTable.STOCK_MOVEMENT to toWire(SyncTable.STOCK_MOVEMENT, movement("m1", "a1", "l1", 1000, item = "i1")),
        )
        assertTrue(dao.applyPull(buffer, next = 42, fullPull = true, now = 99))

        assertTrue(dao.outbox(100).isEmpty())
        val state = dao.syncState()!!
        assertEquals(42, state.since)
        assertEquals(99, state.lastSyncAt)
        assertEquals(false, state.applying)
        // The unit cache follows its movements, without a queued change
        assertEquals("l1", dao.wireRow(SyncTable.ITEM, "i1")!!["locationId"].let { (it as JsonPrimitive).content })
    }

    @Test
    fun aRowWaitingInTheOutboxWinsOverThePull() = runBlocking {
        db.articleDao().insert(article("a1", sku = "LOCAL"))
        val buffer = pull(SyncTable.ARTICLE to toWire(SyncTable.ARTICLE, article("a1", sku = "SERVER")))
        dao.applyPull(buffer, next = 42, fullPull = false, now = 99)
        assertEquals("LOCAL", db.syncDao().article("a1")!!.sku)
        assertEquals(1, dao.outbox(100).size)
    }

    @Test
    fun pushAnswersEmptyTheOutboxAndRecordRefusals() = runBlocking {
        listOf(article("a1", sku = "A"), article("a2", sku = "B"), article("a3", sku = "C")).forEach { db.articleDao().insert(it) }
        val sent = PushRequest("b1", mapOf("article" to listOf("a1", "a2", "a3").map { dao.wireRow(SyncTable.ARTICLE, it)!! }))
        val answer = PushAnswer(
            listOf(
                PushResult("article", "a1", PushStatus.ACCEPTED),
                PushResult("article", "a2", PushStatus.CHANGED, row = buildJsonObject { put("sku", "B (2)") }),
                PushResult("article", "a3", PushStatus.REJECTED, reason = "not-allowed"),
            ),
        )
        val decisions = dao.applyPush(sent, answer, now = 50)

        assertEquals(PushDecision.Done, decisions[0])
        assertTrue(decisions[1] is PushDecision.Write)
        assertEquals("B (2)", dao.article("a2")!!.sku)
        // The server's version is written without queuing it again
        assertTrue(dao.outbox(100).isEmpty())
        val rejected = dao.observeRejectedChangeRows().first().single()
        assertEquals("a3", rejected.change.rowId)
        assertEquals("not-allowed", rejected.change.reason)
        assertEquals("a3", rejected.label)
        assertEquals(1, dao.observeUnseenRejectedCount().first())
        dao.markRejectedSeen(60)
        assertEquals(0, dao.observeUnseenRejectedCount().first())
    }

    @Test
    fun aRowChangedAfterItWasSentStaysQueued() = runBlocking {
        db.articleDao().insert(article("a1", sku = "A"))
        val sent = PushRequest("b1", mapOf("article" to listOf(dao.wireRow(SyncTable.ARTICLE, "a1")!!)))
        db.useWriterConnectionExec("UPDATE article SET sku = 'A2' WHERE id = 'a1'")
        val decisions = dao.applyPush(sent, PushAnswer(listOf(PushResult("article", "a1", PushStatus.ACCEPTED))), now = 50)
        assertEquals(listOf<PushDecision>(PushDecision.Keep), decisions)
        assertEquals(1, dao.outbox(100).size)
    }

    /** A merge while connected queues its rows, never the recomputed unit location (a cache every phone recomputes). */
    @Test
    fun aMergedMovementIsQueuedButNotTheUnitLocation() = runBlocking {
        val buffer = pull(
            SyncTable.LOCATION to toWire(SyncTable.LOCATION, location("l1")),
            SyncTable.LOCATION to toWire(SyncTable.LOCATION, location("l2")),
            SyncTable.ARTICLE to toWire(SyncTable.ARTICLE, article("a1", tracking = Tracking.SERIAL)),
            SyncTable.ITEM to toWire(SyncTable.ITEM, item("i1", "a1", location = "l1")),
            SyncTable.STOCK_MOVEMENT to toWire(SyncTable.STOCK_MOVEMENT, movement("m1", "a1", "l1", 1000, item = "i1", at = 10)),
        )
        dao.applyPull(buffer, next = 42, fullPull = true, now = 99)
        assertTrue(dao.outbox(100).isEmpty())

        val plan = db.backupDao().merge(
            BackupData(
                movements = listOf(
                    movement("m2", "a1", "l1", -1000, item = "i1", at = 20),
                    movement("m3", "a1", "l2", 1000, item = "i1", at = 20),
                ),
            ),
        )
        assertEquals(mapOf<String, String?>("i1" to "l2"), plan.itemLocationFixes)
        assertEquals("l2", (dao.wireRow(SyncTable.ITEM, "i1")!!["locationId"] as JsonPrimitive).content)
        assertEquals(setOf("stock_movement:m2", "stock_movement:m3"), dao.outbox(100).map { "${it.tableName}:${it.rowId}" }.toSet())
        assertEquals(false, dao.syncState()!!.applying)
    }

    private fun withAuthor(row: JsonObject, by: String?, device: String?) = JsonObject(
        row + mapOf("createdBy" to JsonPrimitive(by), "deviceId" to JsonPrimitive(device)),
    )

    /** The one exception to append-only: a pull updates only the author fields of a movement it has. */
    @Test
    fun aPullUpdatesOnlyTheAuthorOfAKnownMovement() = runBlocking {
        val base = listOf(
            SyncTable.LOCATION to toWire(SyncTable.LOCATION, location("l1")),
            SyncTable.ARTICLE to toWire(SyncTable.ARTICLE, article("a1")),
        )
        val m = movement("m1", "a1", "l1", 1000)
        dao.applyPull(pull(*base.toTypedArray(), SyncTable.STOCK_MOVEMENT to toWire(SyncTable.STOCK_MOVEMENT, m)), next = 1, fullPull = true, now = 1)
        assertNull(db.backupDao().snapshot().movements.single().createdBy)

        for (full in listOf(false, true)) {
            val changed = toWire(SyncTable.STOCK_MOVEMENT, m.copy(quantityMilli = 9000, comment = "x"))
            dao.applyPull(pull(SyncTable.STOCK_MOVEMENT to withAuthor(changed, "u1", "d1")), next = 2, fullPull = full, now = 2)
            val kept = db.backupDao().snapshot().movements.single()
            assertEquals(m.copy(createdBy = "u1", deviceId = "d1"), kept)
            // Anonymized by the server: the authors are cleared
            dao.applyPull(pull(SyncTable.STOCK_MOVEMENT to withAuthor(changed, null, null)), next = 3, fullPull = full, now = 3)
            assertEquals(m, db.backupDao().snapshot().movements.single())
        }
        assertTrue(dao.outbox(100).isEmpty())
    }

    @Test
    fun anAcceptedMovementAnswerSetsItsAuthor() = runBlocking {
        dao.applyPull(
            pull(
                SyncTable.LOCATION to toWire(SyncTable.LOCATION, location("l1")),
                SyncTable.ARTICLE to toWire(SyncTable.ARTICLE, article("a1")),
                SyncTable.STOCK_MOVEMENT to toWire(SyncTable.STOCK_MOVEMENT, movement("m1", "a1", "l1", 1000)),
            ),
            next = 1, fullPull = true, now = 1,
        )
        val sent = PushRequest("b1", mapOf("stock_movement" to listOf(dao.wireRow(SyncTable.STOCK_MOVEMENT, "m1")!!)))
        val stored = withAuthor(toWire(SyncTable.STOCK_MOVEMENT, movement("m1", "a1", "l1", 5000)), "u1", "d1")
        val decisions = dao.applyPush(sent, PushAnswer(listOf(PushResult("stock_movement", "m1", PushStatus.ACCEPTED, row = stored))), now = 50)
        assertTrue(decisions.single() is PushDecision.MovementAuthor)
        assertEquals(movement("m1", "a1", "l1", 1000).copy(createdBy = "u1", deviceId = "d1"), db.backupDao().snapshot().movements.single())
        assertTrue(dao.outbox(100).isEmpty())
    }

    @Test
    fun replaceIsRefusedWhileConnected() = runBlocking<Unit> {
        assertTrue(db.backupDao().isConnected())
        assertFailsWith<IllegalStateException> { db.backupDao().replaceAll(BackupData(articles = listOf(article("a1")))) }
        assertNull(dao.article("a1"))
    }
}

private suspend fun AppDatabase.useWriterConnectionExec(sql: String) {
    useWriterConnection { it.execSQL(sql) }
}
