package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dto.sync.PushAnswer
import ch.mathorck.easystock.data.dto.sync.PushRequest
import ch.mathorck.easystock.data.dto.sync.PushResult
import ch.mathorck.easystock.data.model.article.ImageHash
import ch.mathorck.easystock.data.model.server.ServerUser
import ch.mathorck.easystock.data.model.sync.OutboxEntry
import ch.mathorck.easystock.data.model.sync.PendingPush
import ch.mathorck.easystock.data.model.sync.SyncState
import ch.mathorck.easystock.enums.sync.PushStatus
import ch.mathorck.easystock.enums.sync.SyncTable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/** A store with the semantics of RoomSyncStore, on maps of wire rows. */
internal class FakeSyncStore(var state: SyncState?) : SyncStore {
    val rows = SyncTable.entries.associateWith { LinkedHashMap<String, JsonObject>() }
    val outbox = mutableListOf<OutboxEntry>()
    var pending: PendingPush? = null
    val users = mutableMapOf<String, String>()
    val rejected = mutableListOf<PushResult>()
    val images = mutableMapOf<String, ByteArray>()
    val missing = mutableListOf<ImageHash>()
    val saved = mutableMapOf<String, ByteArray>()
    var pullApplies = 0
    val seqs = mutableMapOf<Pair<SyncTable, String>, Long>()

    private fun setSeq(table: SyncTable, id: String, seq: Long?) {
        if (!table.hasServerSeq) return
        if (seq == null) seqs.remove(table to id) else seqs[table to id] = seq
    }

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
    override suspend fun outbox(limit: Int, table: String?): List<OutboxEntry> =
        outbox.filter { table == null || it.tableName == table }.sortedBy { e -> SyncTable.of(e.tableName)!!.ordinal }.take(limit)
    override suspend fun wireRow(table: SyncTable, id: String, hashNewImages: Boolean) = rows.getValue(table)[id]
    override suspend fun imageBytes(sha256: String) = images[sha256]
    override suspend fun serverSeq(table: SyncTable, id: String) = if (table.hasServerSeq) seqs[table to id] else null

    override suspend fun applyPush(sent: PushRequest, answer: PushAnswer, now: Long): List<PushDecision> {
        pending = null
        return answer.results.map { result ->
            val table = SyncTable.of(result.table)!!
            val sentRow = sent.rows[result.table]?.firstOrNull { it.rowId == result.id }
            val decision = decidePush(result, sentRow, rows.getValue(table)[result.id])
            if (result.status == PushStatus.REJECTED && decision != PushDecision.Keep) rejected += result
            when (decision) {
                is PushDecision.Write -> if (table == SyncTable.STOCK_MOVEMENT) {
                    if (keepMovement(result.id, decision.row) && result.status != PushStatus.REJECTED) rejected += result
                } else {
                    rows.getValue(table)[result.id] = decision.row
                }
                PushDecision.DropLocal -> rows.getValue(table).remove(result.id)
                // Append-only: only the author of the local movement is set, other values are reported
                is PushDecision.MovementKept -> if (keepMovement(result.id, decision.server)) rejected += result
                is PushDecision.Conflict -> rows.getValue(table)[result.id] = decision.server
                else -> Unit
            }
            seqUpdates(result, decision).forEach { (id, seq) -> setSeq(table, id, seq) }
            if (decision != PushDecision.Keep) outbox.removeAll { it.tableName == result.table && it.rowId == result.id }
            decision
        }
    }

    /** Sets the author fields of the local movement [id] from [server]; true when the other values differ. */
    private fun keepMovement(id: String, server: JsonObject): Boolean {
        val movements = rows.getValue(SyncTable.STOCK_MOVEMENT)
        val local = movements.getValue(id)
        val authors = setOf("createdBy", "deviceId")
        movements[id] = JsonObject(local + server.filterKeys { it in authors })
        return local.filterKeys { it !in authors } != server.filterKeys { it !in authors && it != WireFields.SERVER_SEQ }
    }

    override suspend fun existingIds(table: SyncTable, ids: Set<String>) = ids.intersect(rows.getValue(table).keys)

    override suspend fun applyPull(buffer: PullBuffer, next: Long, fullPull: Boolean, now: Long, localWins: Boolean): Boolean {
        val current = state ?: return false
        SyncTable.entries.forEach { table ->
            val waiting = outbox.filter { it.tableName == table.tableName }.map { it.rowId }.toSet()
            buffer.rows(table).forEach { row ->
                // A waiting row is pushed, unless the role can't push: then the server's version wins
                if (localWins && row.rowId in waiting) return@forEach
                outbox.removeAll { it.tableName == table.tableName && it.rowId == row.rowId }
                // The real store reads the row through the entity: serverSeq is not a column
                rows.getValue(table)[row.rowId!!] = JsonObject(row - WireFields.SERVER_SEQ)
                row.serverSeq?.let { setSeq(table, row.rowId!!, it) }
            }
        }
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
        users.keys.retainAll(setOf(current.userId))
        rejected.clear()
        images.clear()
        missing.clear()
        seqs.clear()
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
