package ch.mathorck.easystock.sync

import kotlinx.serialization.json.JsonObject

// Pure decisions of the sync engine, shared by the Room store and the test fake.

/** What to do with one pushed row once the server answered. */
sealed interface PushDecision {
    /** Delete the outbox entry, the local row is what the server has. */
    data object Done : PushDecision

    /** Write [row] (the server's version) with `applying` on, then delete the outbox entry. */
    data class Write(val row: JsonObject) : PushDecision

    /**
     * `ATTRIBUTE_MERGED`: the server already had this key under the id of [row]. Write [row], point
     * the references of the local definition [fromId] to it, then delete [fromId] (the server never
     * accepted it, so it is not a synced row).
     */
    data class MergeAttribute(val fromId: String, val row: JsonObject) : PushDecision

    /** `CATEGORY_ATTRIBUTE_SKIPPED`: the category already had this attribute. The local link was never accepted: delete it. */
    data object DropLocal : PushDecision

    /**
     * `MOVEMENT_KEPT`: the server already had a movement with this id and kept its own ([server]).
     * Write [server] (the server never accepted the phone's movement, so it is not a synced row) and
     * record the phone's version [phone] in a `rejected_change`, until the user chooses (sync/MovementKept).
     */
    data class MovementKept(val server: JsonObject, val phone: JsonObject) : PushDecision

    /** The row was changed again after it was read: keep the outbox entry, write nothing. The newer change goes in the next push. */
    data object Keep : PushDecision

    /** The row no longer exists locally: forget the outbox entry. */
    data object Gone : PushDecision
}

/**
 * The decision for [result], given the row [sent] and its [current] local content (both wire rows,
 * read in the same transaction as the writes). A local change made after the row was read is never
 * overwritten: the outbox keeps the time of the first change only, so the content is compared.
 */
fun decidePush(result: PushResult, sent: JsonObject?, current: JsonObject?): PushDecision {
    if (current == null) return PushDecision.Gone
    if (sent == null || current != sent) return PushDecision.Keep
    val row = result.row
    return when (result.status) {
        PushStatus.ACCEPTED -> PushDecision.Done
        PushStatus.CHANGED -> when {
            result.conflict == ConflictCodes.CATEGORY_ATTRIBUTE_SKIPPED -> PushDecision.DropLocal
            result.conflict == ConflictCodes.ATTRIBUTE_MERGED && row?.rowId != null && row.rowId != result.id ->
                PushDecision.MergeAttribute(result.id, sent.overlay(row))
            result.conflict == ConflictCodes.MOVEMENT_KEPT && !row.isNullOrEmpty() && result.table == SyncTable.STOCK_MOVEMENT.tableName ->
                PushDecision.MovementKept(sent.overlay(row), sent)
            row.isNullOrEmpty() -> PushDecision.Done
            else -> PushDecision.Write(sent.overlay(row))
        }
        // The server's version when it has one; a new row it refused stays on this phone only
        PushStatus.REJECTED -> if (row.isNullOrEmpty()) PushDecision.Done else PushDecision.Write(current.overlay(row))
    }
}

/** "value (2)", "value (3)"... the first one [isFree] accepts, cut to [maxLength] (same rule as backup/BackupMerge). */
inline fun suffixedValue(value: String, maxLength: Int, isFree: (String) -> Boolean): String {
    var n = 2
    while (true) {
        val suffix = " ($n)"
        val candidate = value.take(maxLength - suffix.length).trimEnd() + suffix
        if (isFree(candidate)) return candidate
        n++
    }
}

/**
 * Pulled pages kept until their rows can be written: a row may point to a parent that comes in a
 * later page (a category renamed after its articles were created has a higher `server_seq`).
 * Rows by table, in arrival order; a row seen again replaces the older copy and moves to the end.
 */
class PullBuffer {
    private val rows = SyncTable.entries.associateWith { LinkedHashMap<String, JsonObject>() }

    val users = LinkedHashMap<String, WireUser>()
    val alerts = LinkedHashMap<String, WireAlert>()

    var rowCount: Int = 0
        private set

    val isEmpty: Boolean get() = rows.values.all { it.isEmpty() } && users.isEmpty() && alerts.isEmpty()

    fun add(answer: PullAnswer) {
        answer.rows.forEach { (tableName, list) ->
            // A table this app doesn't know (newer server): nothing to write it to
            val table = SyncTable.of(tableName) ?: return@forEach
            val target = rows.getValue(table)
            list.forEach { row ->
                val id = row.rowId ?: return@forEach
                target.remove(id)
                target[id] = row
                rowCount++
            }
        }
        answer.users.forEach { users[it.id] = it }
        answer.alerts.forEach { alerts[it.id] = it }
    }

    fun rows(table: SyncTable): List<JsonObject> = rows.getValue(table).values.toList()

    fun ids(table: SyncTable): Set<String> = rows.getValue(table).keys

    /** Referenced ids by table name, of every buffered row. */
    fun references(): Map<String, Set<String>> {
        val refs = HashMap<String, HashSet<String>>()
        SyncTable.entries.forEach { table ->
            rows.getValue(table).values.forEach { row ->
                table.references.forEach { (column, target) -> row.column(column)?.let { refs.getOrPut(target) { HashSet() } += it } }
            }
        }
        return refs
    }

    /** Referenced ids neither buffered nor in [existing] (ids already stored on the phone), by table name. */
    suspend fun missingReferences(existing: suspend (SyncTable, Set<String>) -> Set<String>): Map<String, Set<String>> =
        references().mapNotNull { (tableName, ids) ->
            val table = SyncTable.of(tableName) ?: return@mapNotNull null
            val notBuffered = ids - ids(table)
            if (notBuffered.isEmpty()) return@mapNotNull null
            val missing = notBuffered - existing(table, notBuffered)
            if (missing.isEmpty()) null else tableName to missing
        }.toMap()

    fun clear() {
        rows.values.forEach { it.clear() }
        users.clear()
        alerts.clear()
        rowCount = 0
    }
}
