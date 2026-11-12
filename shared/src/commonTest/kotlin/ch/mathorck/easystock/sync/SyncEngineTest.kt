package ch.mathorck.easystock.sync

import ch.mathorck.easystock.auth.InMemorySessionStore
import ch.mathorck.easystock.data.model.ImageHash
import ch.mathorck.easystock.data.model.OutboxEntry
import ch.mathorck.easystock.data.model.PendingPush
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.server.ClientVersions
import ch.mathorck.easystock.server.KtorAccountApi
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.createServerClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** A store with the semantics of RoomSyncStore, on maps of wire rows. */
private class FakeSyncStore(var state: SyncState?) : SyncStore {
    val rows = SyncTable.entries.associateWith { LinkedHashMap<String, JsonObject>() }
    val outbox = mutableListOf<OutboxEntry>()
    var pending: PendingPush? = null
    val users = mutableMapOf<String, String>()
    val rejected = mutableListOf<PushResult>()
    val images = mutableMapOf<String, ByteArray>()
    val missing = mutableListOf<ImageHash>()
    val saved = mutableMapOf<String, ByteArray>()
    var pullApplies = 0

    fun put(table: SyncTable, row: JsonObject, queue: Boolean = true) {
        rows.getValue(table)[row.rowId!!] = row
        if (queue && outbox.none { it.tableName == table.tableName && it.rowId == row.rowId }) {
            outbox += OutboxEntry(table.tableName, row.rowId!!, 1)
        }
    }

    override suspend fun syncState() = state
    override suspend fun updateRole(role: String) {
        state = state?.copy(role = role)
    }
    override suspend fun upsertUser(user: ServerUser) {
        users[user.userId] = user.displayName
    }
    override suspend fun pendingPush() = pending
    override suspend fun savePendingPush(push: PendingPush) {
        pending = push
    }
    override suspend fun clearPendingPush() {
        pending = null
    }
    override suspend fun outbox(limit: Int): List<OutboxEntry> =
        outbox.sortedBy { e -> SyncTable.of(e.tableName)!!.ordinal }.take(limit)
    override suspend fun wireRow(table: SyncTable, id: String, hashNewImages: Boolean) = rows.getValue(table)[id]
    override suspend fun imageBytes(sha256: String) = images[sha256]

    override suspend fun applyPush(sent: PushRequest, answer: PushAnswer, now: Long): List<PushDecision> {
        pending = null
        return answer.results.map { result ->
            val table = SyncTable.of(result.table)!!
            val sentRow = sent.rows[result.table]?.firstOrNull { it.rowId == result.id }
            val decision = decidePush(result, sentRow, rows.getValue(table)[result.id])
            if (result.status == PushStatus.REJECTED && decision != PushDecision.Keep) rejected += result
            when (decision) {
                is PushDecision.Write -> rows.getValue(table)[result.id] = decision.row
                PushDecision.DropLocal -> rows.getValue(table).remove(result.id)
                else -> Unit
            }
            if (decision != PushDecision.Keep) outbox.removeAll { it.tableName == result.table && it.rowId == result.id }
            decision
        }
    }

    override suspend fun existingIds(table: SyncTable, ids: Set<String>) = ids.intersect(rows.getValue(table).keys)

    override suspend fun applyPull(buffer: PullBuffer, next: Long, fullPull: Boolean, now: Long): Boolean {
        val current = state ?: return false
        SyncTable.entries.forEach { table -> buffer.rows(table).forEach { rows.getValue(table)[it.rowId!!] = it } }
        buffer.users.values.forEach { users[it.id] = it.displayName }
        state = current.copy(since = next, lastSyncAt = now)
        pullApplies++
        return true
    }

    var wipes = 0

    override suspend fun wipeServerData(): Boolean {
        val current = state ?: return false
        rows.values.forEach { it.clear() }
        outbox.clear()
        pending = null
        users.clear()
        rejected.clear()
        images.clear()
        missing.clear()
        state = current.copy(since = 0)
        wipes++
        return true
    }

    override suspend fun missingImages() = missing.toList()
    override suspend fun saveImage(image: ImageHash, jpeg: ByteArray) {
        saved[image.sha256] = jpeg
        missing.remove(image)
    }
}

class SyncEngineTest {
    private val base = "http://192.168.1.20:8080"
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val session = InMemorySessionStore()
    private var revokedCalls = 0
    private var batchIds = 0

    private data class Seen(val method: HttpMethod, val path: String, val query: String, val auth: String?, val app: String?, val schema: String?, val body: String, val epoch: String? = null)

    private val seen = mutableListOf<Seen>()

    private suspend fun record(request: HttpRequestData): Seen {
        val body = when (val content = request.body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readByteArray().decodeToString()
            else -> request.body.toByteArray().decodeToString()
        }
        return Seen(
            request.method,
            request.url.encodedPath.removePrefix("/api/v1/"),
            request.url.encodedQuery,
            request.headers[HttpHeaders.Authorization],
            request.headers[ClientVersions.APP_HEADER],
            request.headers[ClientVersions.SCHEMA_HEADER],
            body,
            request.headers[DATASET_EPOCH_HEADER],
        ).also { seen += it }
    }

    private fun state(role: String = "editor", since: Long = 7) = SyncState(serverUrl = base, userId = "u1", role = role, since = since)

    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
    }

    private fun TestScope.engine(
        store: SyncStore,
        epochs: DatasetEpochStore = InMemoryDatasetEpochStore(),
        handler: suspend MockRequestHandleScope.(Seen) -> HttpResponseData,
    ): SyncEngine {
        session.accessToken = "access-1"
        val engine = MockEngine { request -> handler(record(request)) }
        val client = createServerClient(
            ServerConnection(base),
            engine,
            session,
            ClientVersions("1.2.3", schemaVersion = 10),
            onSessionRevoked = { revokedCalls++ },
        )
        val remote = SyncRemote(KtorSyncApi(client), KtorAccountApi(client))
        return SyncEngine(store, { remote }, backgroundScope, clock, newBatchId = { "batch-${++batchIds}" }, epochs = epochs)
    }

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK, json)

    private fun me(role: String = "member") = """{"id":"u1","displayName":"Ann","role":"$role","kind":"user"}"""
    private val emptyPull = """{"rows":{},"next":7,"hasMore":false}"""

    /** Default answers: /me, an empty pull, accepted pushes. */
    private fun MockRequestHandleScope.defaults(request: Seen, pushAnswer: String = """{"results":[]}"""): HttpResponseData = when (request.path) {
        "me" -> ok(me())
        "sync/pull" -> ok(emptyPull)
        "sync/push" -> ok(pushAnswer)
        else -> respond("", HttpStatusCode.NotFound)
    }

    private fun row(id: String, vararg fields: Pair<String, Any?>): JsonObject = buildJsonObject {
        put("id", id)
        fields.forEach { (k, v) ->
            when (v) {
                null -> put(k, kotlinx.serialization.json.JsonNull)
                is Number -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }

    @Test
    fun notConnectedMakesNoRequest() = runTest {
        val engine = engine(FakeSyncStore(null)) { defaults(it) }
        assertEquals(SyncResult.NOT_CONNECTED, engine.sync())
        assertTrue(seen.isEmpty())
    }

    @Test
    fun pushesParentsFirstWithTheVersionHeadersThenPulls() = runTest {
        val store = FakeSyncStore(state())
        store.put(SyncTable.ARTICLE, row("a1", "categoryId" to "c1", "imageSha256" to null))
        store.put(SyncTable.CATEGORY, row("c1", "name" to "Bolts"))
        val engine = engine(store) { request ->
            defaults(request, """{"results":[{"table":"category","id":"c1","status":"accepted"},{"table":"article","id":"a1","status":"accepted"}]}""")
        }
        assertEquals(SyncResult.DONE, engine.sync())

        assertEquals(listOf("me", "sync/push", "sync/pull"), seen.map { it.path })
        val push = seen[1]
        assertEquals(HttpMethod.Post, push.method)
        assertEquals("Bearer access-1", push.auth)
        assertEquals("1.2.3", push.app)
        assertEquals("10", push.schema)
        val sent = decodePush(push.body)
        assertEquals("batch-1", sent.batchId)
        assertEquals(listOf("category", "article"), sent.rows.keys.toList())
        assertEquals("since=7&limit=500", seen[2].query)
        assertTrue(store.outbox.isEmpty())
        assertNull(store.pending)
        assertEquals(1_000, store.state!!.lastSyncAt)
        assertEquals("Ann", store.users["u1"])
    }

    @Test
    fun aBatchLostOnTheNetworkIsSentAgainIdentical() = runTest {
        val store = FakeSyncStore(state())
        store.put(SyncTable.CATEGORY, row("c1", "name" to "Bolts"))
        var fail = true
        val engine = engine(store) { request ->
            if (request.path == "sync/push" && fail) throw IOException("lost")
            defaults(request, """{"results":[{"table":"category","id":"c1","status":"accepted"}]}""")
        }
        assertEquals(SyncResult.FAILED, engine.sync())
        assertEquals(SyncError.NETWORK, engine.status.value.error)
        assertNotNull(store.pending)
        assertEquals(1, store.outbox.size)

        fail = false
        assertEquals(SyncResult.DONE, engine.sync())
        val pushes = seen.filter { it.path == "sync/push" }
        assertEquals(2, pushes.size)
        assertEquals(pushes[0].body, pushes[1].body)
        assertEquals(1, batchIds)
        assertNull(engine.status.value.error)
        assertTrue(store.outbox.isEmpty())
    }

    @Test
    fun changedAndRejectedRowsTakeTheServerVersion() = runTest {
        val store = FakeSyncStore(state())
        store.put(SyncTable.ARTICLE, row("a1", "sku" to "BLT", "name" to "Bolt", "imageSha256" to null))
        store.put(SyncTable.ARTICLE, row("a2", "sku" to "NUT", "name" to "Nut", "imageSha256" to null))
        store.put(SyncTable.ARTICLE, row("a3", "sku" to "NEW", "name" to "New", "imageSha256" to null))
        val answer = """{"results":[
            {"table":"article","id":"a1","status":"changed","row":{"sku":"BLT (2)"}},
            {"table":"article","id":"a2","status":"rejected","reason":"not-allowed","row":{"id":"a2","sku":"NUT","name":"Old nut","imageSha256":null}},
            {"table":"article","id":"a3","status":"rejected","reason":"invalid"}
        ]}"""
        val engine = engine(store) { defaults(it, answer) }
        assertEquals(SyncResult.DONE, engine.sync())

        val articles = store.rows.getValue(SyncTable.ARTICLE)
        assertEquals(JsonPrimitive("BLT (2)"), articles["a1"]!!["sku"])
        assertEquals(JsonPrimitive("Bolt"), articles["a1"]!!["name"])
        assertEquals(JsonPrimitive("Old nut"), articles["a2"]!!["name"])
        // A new row the server refused stays on the phone only
        assertEquals(JsonPrimitive("New"), articles["a3"]!!["name"])
        assertEquals(listOf("a2", "a3"), store.rejected.map { it.id })
        assertTrue(store.outbox.isEmpty())
    }

    @Test
    fun aViewerNeverPushes() = runTest {
        val store = FakeSyncStore(state(role = "viewer"))
        store.put(SyncTable.CATEGORY, row("c1", "name" to "Bolts"))
        val engine = engine(store) { request -> if (request.path == "me") ok(me("viewer")) else defaults(request) }
        assertEquals(SyncResult.DONE, engine.sync())
        assertEquals(listOf("me", "sync/pull"), seen.map { it.path })
        assertEquals(1, store.outbox.size)
    }

    @Test
    fun aRoleChangedByAnAdminIsStored() = runTest {
        val store = FakeSyncStore(state(role = "editor"))
        store.put(SyncTable.CATEGORY, row("c1", "name" to "Bolts"))
        val engine = engine(store) { request -> if (request.path == "me") ok(me("viewer")) else defaults(request) }
        engine.sync()
        assertEquals("viewer", store.state!!.role)
        // Already a viewer for this cycle: nothing pushed
        assertTrue(seen.none { it.path == "sync/push" })
    }

    @Test
    fun pagesAreWrittenOnceTheirParentsArrived() = runTest {
        val store = FakeSyncStore(state(since = 0))
        val pages = mapOf(
            "0" to """{"rows":{"article":[{"id":"a1","categoryId":"c1"}]},"next":4,"hasMore":true}""",
            "4" to """{"rows":{"category":[{"id":"c1","name":"Bolts"}]},"users":[{"id":"u2","displayName":"Bob"}],"next":9,"hasMore":true}""",
            "9" to """{"rows":{"category":[{"id":"c2","name":"Nuts"}]},"next":12,"hasMore":false}""",
        )
        val rowsWhenAsked = mutableListOf<Int?>()
        lateinit var engine: SyncEngine
        engine = engine(store) { request ->
            if (request.path == "sync/pull") {
                rowsWhenAsked += engine.status.value.fullPullRows
                ok(pages.getValue(request.query.substringAfter("since=").substringBefore("&")))
            } else defaults(request)
        }
        assertEquals(SyncResult.DONE, engine.sync())
        assertEquals(listOf("since=0&limit=500", "since=4&limit=500", "since=9&limit=500"), seen.filter { it.path == "sync/pull" }.map { it.query })
        // Page 1 waited for its category: one write for pages 1-2, one for page 3
        assertEquals(2, store.pullApplies)
        assertEquals(listOf<Int?>(0, 0, 2), rowsWhenAsked)
        assertEquals(12, store.state!!.since)
        assertEquals(setOf("a1"), store.rows.getValue(SyncTable.ARTICLE).keys)
        assertEquals("Bob", store.users["u2"])
        assertNull(engine.status.value.fullPullRows)
        assertEquals(false, engine.status.value.running)
    }

    @Test
    fun aFailedPageLeavesTheCursorWhereItWas() = runTest {
        val store = FakeSyncStore(state(since = 3))
        val engine = engine(store) { request ->
            when {
                request.path != "sync/pull" -> defaults(request)
                request.query.startsWith("since=3") -> ok("""{"rows":{"article":[{"id":"a1","categoryId":"c1"}]},"next":5,"hasMore":true}""")
                else -> respond("", HttpStatusCode.InternalServerError)
            }
        }
        assertEquals(SyncResult.FAILED, engine.sync())
        assertEquals(3, store.state!!.since)
        assertTrue(store.rows.getValue(SyncTable.ARTICLE).isEmpty())
        assertEquals(SyncError.SERVER, engine.status.value.error)
    }

    @Test
    fun refreshesTheTokenOn401ThenGoesOn() = runTest {
        session.saveRefreshToken("refresh-1")
        val store = FakeSyncStore(state())
        val engine = engine(store) { request ->
            when {
                request.path == "auth/refresh" -> ok("""{"accessToken":"access-2","refreshToken":"refresh-2"}""")
                request.auth == "Bearer access-1" -> respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
                else -> defaults(request)
            }
        }
        assertEquals(SyncResult.DONE, engine.sync())
        assertEquals(listOf("me", "auth/refresh", "me", "sync/pull"), seen.map { it.path })
        assertEquals("refresh-2", session.refreshToken())
    }

    @Test
    fun aRevokedSessionStopsTheCycle() = runTest {
        session.saveRefreshToken("refresh-1")
        val store = FakeSyncStore(state())
        val engine = engine(store) { request ->
            if (request.path == "auth/refresh") {
                respond("""{"type":"session-revoked","status":401}""", HttpStatusCode.Unauthorized, json)
            } else {
                respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
            }
        }
        assertEquals(SyncResult.FAILED, engine.sync())
        assertEquals(1, revokedCalls)
        assertEquals(SyncError.SESSION_REVOKED, engine.status.value.error)
        assertTrue(seen.none { it.path.startsWith("sync/") })
    }

    @Test
    fun appTooOldPausesTheSyncForThatServer() = runTest {
        val store = FakeSyncStore(state())
        val engine = engine(store) { respond("""{"type":"app-too-old","status":426}""", HttpStatusCode.UpgradeRequired, json) }
        assertEquals(SyncResult.PAUSED, engine.sync())
        assertTrue(engine.status.value.paused)
        assertEquals(SyncError.APP_TOO_OLD, engine.status.value.error)
        val requests = seen.size

        assertEquals(SyncResult.PAUSED, engine.sync())
        assertEquals(requests, seen.size)

        // Left the server: the pause is forgotten
        store.state = null
        assertEquals(SyncResult.NOT_CONNECTED, engine.sync())
        assertEquals(SyncStatus(), engine.status.value)
    }

    @Test
    fun uploadsOnlyThePhotosTheServerLacks() = runTest {
        val store = FakeSyncStore(state())
        val known = "a".repeat(64)
        val unknown = "b".repeat(64)
        store.put(SyncTable.ARTICLE, row("a1", "imageSha256" to known))
        store.put(SyncTable.ARTICLE, row("a2", "imageSha256" to unknown))
        store.images[unknown] = byteArrayOf(1, 2, 3)
        store.images[known] = byteArrayOf(4)
        val engine = engine(store) { request ->
            when {
                request.method == HttpMethod.Head && request.path == "images/$known" -> respond("", HttpStatusCode.OK)
                request.method == HttpMethod.Head -> respond("", HttpStatusCode.NotFound)
                request.method == HttpMethod.Put -> respond("", HttpStatusCode.Created)
                else -> defaults(request, """{"results":[{"table":"article","id":"a1","status":"accepted"},{"table":"article","id":"a2","status":"accepted"}]}""")
            }
        }
        assertEquals(SyncResult.DONE, engine.sync())
        val puts = seen.filter { it.method == HttpMethod.Put }
        assertEquals(listOf("images/$unknown"), puts.map { it.path })
        // Photos go up before the rows that point to them
        assertTrue(seen.indexOfFirst { it.method == HttpMethod.Put } < seen.indexOfFirst { it.path == "sync/push" })
    }

    @Test
    fun downloadsMissingPhotosAndChecksTheirHash() = runTest {
        val good = byteArrayOf(1, 2, 3)
        val goodSha = sha256Hex(good)
        val badSha = "c".repeat(64)
        val store = FakeSyncStore(state())
        store.missing += ImageHash("images/1.jpg", goodSha)
        store.missing += ImageHash("images/2.jpg", badSha)
        session.accessToken = "access-1"
        val engine = engine(store) { request ->
            when (request.path) {
                "images/$goodSha" -> respond(good, HttpStatusCode.OK)
                "images/$badSha" -> respond(byteArrayOf(9), HttpStatusCode.OK)
                else -> defaults(request)
            }
        }
        assertEquals(SyncResult.DONE, engine.sync())
        engine.awaitDownloads()
        assertContentEquals(good, store.saved[goodSha])
        assertNull(store.saved[badSha])
        assertEquals(1, store.saved.size)
    }

    // dataset-reset: the server replaced its whole data set

    private fun MockRequestHandleScope.datasetReset() =
        respond("""{"type":"dataset-reset","status":409}""", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/problem+json"))

    private suspend fun epochStore(epoch: String?) = InMemoryDatasetEpochStore().also { it.save(epoch) }

    @Test
    fun aDatasetResetOnPullWipesThePhoneAndPullsEverythingAgain() = runTest {
        val store = FakeSyncStore(state(since = 7))
        store.put(SyncTable.CATEGORY, row("c1", "name" to "Old"), queue = false)
        store.users["u9"] = "Old user"
        val epochs = epochStore("e1")
        val engine = engine(store, epochs) { request ->
            when {
                request.path != "sync/pull" -> defaults(request)
                request.epoch == "e1" -> datasetReset()
                else -> ok("""{"rows":{"category":[{"id":"c9","name":"New"}]},"next":20,"hasMore":false,"datasetEpoch":"e2"}""")
            }
        }
        assertEquals(SyncResult.DONE, engine.sync())

        val pulls = seen.filter { it.path == "sync/pull" }
        assertEquals(listOf("since=7&limit=500", "since=0&limit=500"), pulls.map { it.query })
        // The full pull starts a new data set: no epoch sent
        assertEquals(listOf("e1", null), pulls.map { it.epoch })
        assertEquals(1, store.wipes)
        assertEquals(setOf("c9"), store.rows.getValue(SyncTable.CATEGORY).keys)
        assertNull(store.users["u9"])
        assertEquals(20, store.state!!.since)
        assertEquals("e2", epochs.load())
        assertEquals(DatasetResetNotice(lostChanges = 0), engine.resetNotice.value)
        assertNull(engine.status.value.error)
        // Still connected: same session, no revoke
        assertEquals(0, revokedCalls)
        assertEquals("access-1", session.accessToken)
        assertNotNull(store.state)

        engine.resetNoticeShown()
        assertNull(engine.resetNotice.value)
    }

    @Test
    fun aDatasetResetOnPushCountsTheLostChanges() = runTest {
        val store = FakeSyncStore(state(since = 7))
        store.put(SyncTable.CATEGORY, row("c1", "name" to "Bolts"))
        store.put(SyncTable.ARTICLE, row("a1", "categoryId" to "c1", "imageSha256" to null))
        val engine = engine(store, epochStore("e1")) { request ->
            when (request.path) {
                "sync/push" -> datasetReset()
                "sync/pull" -> ok("""{"rows":{},"next":30,"hasMore":false,"datasetEpoch":"e2"}""")
                else -> defaults(request)
            }
        }
        assertEquals(SyncResult.DONE, engine.sync())

        assertEquals("e1", seen.single { it.path == "sync/push" }.epoch)
        assertEquals(listOf("since=0&limit=500"), seen.filter { it.path == "sync/pull" }.map { it.query })
        assertEquals(DatasetResetNotice(lostChanges = 2), engine.resetNotice.value)
        assertTrue(store.outbox.isEmpty())
        assertNull(store.pending)
        assertTrue(store.rows.values.all { it.isEmpty() })
        assertEquals(30, store.state!!.since)
    }

    @Test
    fun theEpochOfAFullPullIsKeptAndSentAfterwards() = runTest {
        val store = FakeSyncStore(state(since = 0))
        val epochs = epochStore("stale")
        val engine = engine(store, epochs) { request ->
            if (request.path == "sync/pull") ok("""{"rows":{},"next":7,"hasMore":false,"datasetEpoch":"e1"}""") else defaults(request)
        }
        assertEquals(SyncResult.DONE, engine.sync())
        assertEquals("e1", epochs.load())

        store.put(SyncTable.CATEGORY, row("c1", "name" to "Bolts"))
        assertEquals(SyncResult.DONE, engine.sync())
        assertEquals(listOf(null, "e1"), seen.filter { it.path == "sync/pull" }.map { it.epoch })
        assertEquals("e1", seen.single { it.path == "sync/push" }.epoch)
        assertEquals(0, store.wipes)
        assertNull(engine.resetNotice.value)
    }

    @Test
    fun aPullAnswerWithAnotherEpochStartsOver() = runTest {
        val store = FakeSyncStore(state(since = 7))
        store.put(SyncTable.CATEGORY, row("c1", "name" to "Old"), queue = false)
        val epochs = epochStore("e1")
        val engine = engine(store, epochs) { request ->
            when {
                request.path != "sync/pull" -> defaults(request)
                // A server that doesn't check the header
                request.query.startsWith("since=7") -> ok("""{"rows":{"category":[{"id":"c5","name":"Partial"}]},"next":9,"hasMore":false,"datasetEpoch":"e2"}""")
                else -> ok("""{"rows":{"category":[{"id":"c9","name":"New"}]},"next":40,"hasMore":false,"datasetEpoch":"e2"}""")
            }
        }
        assertEquals(SyncResult.DONE, engine.sync())
        assertEquals(1, store.wipes)
        assertEquals(setOf("c9"), store.rows.getValue(SyncTable.CATEGORY).keys)
        assertEquals(40, store.state!!.since)
        assertEquals("e2", epochs.load())
        assertNotNull(engine.resetNotice.value)
    }

    @Test
    fun aSecondDatasetResetInOneCycleFailsWithoutWipingAgain() = runTest {
        val store = FakeSyncStore(state(since = 7))
        val engine = engine(store, epochStore("e1")) { request ->
            if (request.path == "sync/pull") datasetReset() else defaults(request)
        }
        assertEquals(SyncResult.FAILED, engine.sync())
        assertEquals(1, store.wipes)
        assertEquals(SyncError.SERVER, engine.status.value.error)
        assertEquals(0, store.state!!.since)
        assertEquals(false, engine.status.value.running)
        assertNull(engine.status.value.fullPullRows)
    }
}
