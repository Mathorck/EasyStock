package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dto.sync.PullAnswer
import ch.mathorck.easystock.data.dto.sync.WireAlert
import ch.mathorck.easystock.data.dto.sync.WireUser
import ch.mathorck.easystock.enums.sync.SyncTable
import kotlinx.serialization.json.JsonObject

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
