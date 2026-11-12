package ch.mathorck.easystock.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncPlanTest {
    private fun row(id: String, vararg fields: Pair<String, String?>): JsonObject = buildJsonObject {
        put("id", id)
        fields.forEach { (k, v) -> put(k, v) }
    }

    private fun result(status: PushStatus, row: JsonObject? = null, conflict: String? = null, id: String = "r1") =
        PushResult(table = "article", id = id, status = status, conflict = conflict, row = row)

    private val sent = row("r1", "name" to "Bolt", "sku" to "BLT")

    @Test
    fun aRowGoneLocallyIsForgotten() {
        assertEquals(PushDecision.Gone, decidePush(result(PushStatus.ACCEPTED), sent, null))
    }

    @Test
    fun aRowChangedAgainIsKept() {
        val current = row("r1", "name" to "Bolt M8", "sku" to "BLT")
        assertEquals(PushDecision.Keep, decidePush(result(PushStatus.ACCEPTED), sent, current))
        assertEquals(PushDecision.Keep, decidePush(result(PushStatus.REJECTED), sent, current))
        assertEquals(PushDecision.Keep, decidePush(result(PushStatus.ACCEPTED), null, current))
    }

    @Test
    fun acceptedIsDone() {
        assertEquals(PushDecision.Done, decidePush(result(PushStatus.ACCEPTED), sent, sent))
    }

    @Test
    fun changedWritesTheServerFieldsOverTheSentRow() {
        val decision = decidePush(result(PushStatus.CHANGED, buildJsonObject { put("sku", "BLT (2)") }), sent, sent)
        assertEquals(PushDecision.Write(row("r1", "name" to "Bolt", "sku" to "BLT (2)")), decision)
        assertEquals(PushDecision.Done, decidePush(result(PushStatus.CHANGED, JsonObject(emptyMap())), sent, sent))
        assertEquals(PushDecision.Done, decidePush(result(PushStatus.CHANGED), sent, sent))
    }

    @Test
    fun changedConflictCodes() {
        assertEquals(
            PushDecision.DropLocal,
            decidePush(result(PushStatus.CHANGED, conflict = ConflictCodes.CATEGORY_ATTRIBUTE_SKIPPED), sent, sent),
        )
        val merged = decidePush(result(PushStatus.CHANGED, row("server-id"), ConflictCodes.ATTRIBUTE_MERGED), sent, sent)
        assertEquals(PushDecision.MergeAttribute("r1", row("server-id", "name" to "Bolt", "sku" to "BLT")), merged)
        // Same id: a plain change
        val same = decidePush(result(PushStatus.CHANGED, row("r1", "name" to "X"), ConflictCodes.ATTRIBUTE_MERGED), sent, sent)
        assertEquals(PushDecision.Write(row("r1", "name" to "X", "sku" to "BLT")), same)
    }

    @Test
    fun rejectedTakesTheServerVersionOrKeepsTheNewRow() {
        assertEquals(PushDecision.Done, decidePush(result(PushStatus.REJECTED), sent, sent))
        val server = row("r1", "name" to "Old bolt", "sku" to "OLD")
        assertEquals(PushDecision.Write(server), decidePush(result(PushStatus.REJECTED, server), sent, sent))
    }

    @Test
    fun theSentBaseSeqIsNotALocalChange() {
        assertEquals(PushDecision.Done, decidePush(result(PushStatus.ACCEPTED), sent.withBaseSeq(5), sent))
    }

    @Test
    fun aConflictWritesTheServerRowAndKeepsThePhoneOne() {
        val server = row("r1", "name" to "Theirs", "sku" to "BLT")
        val conflict = PushResult("article", "r1", PushStatus.REJECTED, reason = CONFLICT_REASON, row = server, serverSeq = 9)
        assertEquals(PushDecision.Conflict(server, sent, 9), decidePush(conflict, sent.withBaseSeq(5), sent))
        assertEquals(mapOf("r1" to 9L), seqUpdates(conflict, PushDecision.Conflict(server, sent, 9)))
        // No seq: the plain server version
        assertEquals(PushDecision.Write(server), decidePush(conflict.copy(serverSeq = null), sent, sent))
        // Movements have their own rule (MovementKept)
        val movement = conflict.copy(table = "stock_movement")
        assertTrue(decidePush(movement, sent, sent) !is PushDecision.Conflict)
    }

    @Test
    fun theStoredSeqFollowsThePushResult() {
        val accepted = result(PushStatus.ACCEPTED).copy(serverSeq = 40)
        assertEquals(mapOf("r1" to 40L), seqUpdates(accepted, PushDecision.Done))
        // Changed again meanwhile: still on the server's seq, the next push is on top of it
        assertEquals(mapOf("r1" to 40L), seqUpdates(accepted, PushDecision.Keep))
        // Refused and changed again: the stored seq stays what it was
        assertEquals(emptyMap(), seqUpdates(result(PushStatus.REJECTED).copy(serverSeq = 40), PushDecision.Keep))
        assertEquals(mapOf("r1" to null), seqUpdates(accepted, PushDecision.DropLocal))
        val merged = PushDecision.MergeAttribute("r1", row("server-id"))
        assertEquals(mapOf("r1" to null, "server-id" to 40L), seqUpdates(accepted, merged))
        assertEquals(emptyMap(), seqUpdates(accepted.copy(table = "stock_movement"), PushDecision.Done))
    }

    @Test
    fun suffixedValueTakesTheFirstFreeOneWithinTheLength() {
        val taken = setOf("Bolt (2)", "Bolt (3)")
        assertEquals("Bolt (4)", suffixedValue("Bolt", 40) { it !in taken })
        assertEquals("abcd (2)", suffixedValue("abcdefgh", 8) { true })
        assertEquals("ab (2)", suffixedValue("ab  xy", 6) { true })
    }

    @Test
    fun theBufferKeepsTheLastCopyAndFindsMissingParents() = runTest {
        val buffer = PullBuffer()
        buffer.add(
            PullAnswer(
                rows = mapOf(
                    "article" to listOf(row("a1", "categoryId" to "c1"), row("a2", "categoryId" to "c2")),
                    "unknown_table" to listOf(row("x")),
                ),
                users = listOf(WireUser("u1", "Ann")),
                next = 3,
                hasMore = true,
            ),
        )
        assertEquals(2, buffer.rowCount)
        // c2 is already on the phone, c1 nowhere yet
        assertEquals(mapOf("category" to setOf("c1")), buffer.missingReferences { _, ids -> ids.intersect(setOf("c2")) })

        buffer.add(
            PullAnswer(
                rows = mapOf(
                    "category" to listOf(row("c1")),
                    "article" to listOf(row("a1", "categoryId" to "c1", "name" to "new")),
                ),
                next = 5,
            ),
        )
        assertTrue(buffer.missingReferences { _, ids -> ids.intersect(setOf("c2")) }.isEmpty())
        assertEquals(listOf("a2", "a1"), buffer.rows(SyncTable.ARTICLE).map { it.rowId })
        assertEquals(JsonPrimitive("new"), buffer.rows(SyncTable.ARTICLE).last()["name"])
        assertEquals(setOf("u1"), buffer.users.keys)

        buffer.clear()
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.rowCount)
    }
}
