package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dto.sync.PushResult
import ch.mathorck.easystock.enums.sync.PushStatus
import ch.mathorck.easystock.enums.sync.SyncTable
import kotlinx.serialization.json.JsonObject

/**
 * The decision for [result], given the row [sent] and its [current] local content (both wire rows,
 * read in the same transaction as the writes). A local change made after the row was read is never
 * overwritten: the outbox keeps the time of the first change only, so the content is compared.
 */
fun decidePush(result: PushResult, sentWithSeq: JsonObject?, current: JsonObject?): PushDecision {
    if (current == null) return PushDecision.Gone
    // The `baseSeq` is not row content: [current] never has one
    val sent = sentWithSeq?.withoutBaseSeq()
    if (sent == null || current != sent) return PushDecision.Keep
    val row = result.row
    return when (result.status) {
        PushStatus.ACCEPTED -> when {
            row.isNullOrEmpty() -> PushDecision.Done
            result.table == SyncTable.STOCK_MOVEMENT.tableName -> PushDecision.MovementAuthor(row)
            // The server set `created_by` of a new flag: store its row
            result.table == SyncTable.ARTICLE_FLAG.tableName -> PushDecision.Write(sent.overlay(row))
            else -> PushDecision.Done
        }
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
        PushStatus.REJECTED -> when {
            row.isNullOrEmpty() -> PushDecision.Done
            result.reason == CONFLICT_REASON && result.serverSeq != null && SyncTable.of(result.table)?.hasServerSeq == true ->
                PushDecision.Conflict(current.overlay(row), sent, result.serverSeq)
            else -> PushDecision.Write(current.overlay(row))
        }
    }
}

/**
 * The `server_seq` values to store once [decision] was applied for [result]: row id to seq, null to
 * forget it. Empty for the tables without a seq (movements). The seq of a result is the server row's,
 * so it goes with the row the phone now holds (the server's id after `ATTRIBUTE_MERGED`).
 */
fun seqUpdates(result: PushResult, decision: PushDecision): Map<String, Long?> {
    if (SyncTable.of(result.table)?.hasServerSeq != true) return emptyMap()
    return when (decision) {
        PushDecision.Done, is PushDecision.Write -> mapOf(result.id to result.serverSeq)
        is PushDecision.Conflict -> mapOf(result.id to decision.seq)
        // The server took the sent version: the newer local change is based on it. A rejected row is not
        // (a stale edit then conflicts again at the next push)
        PushDecision.Keep -> if (result.status != PushStatus.REJECTED && result.serverSeq != null) mapOf(result.id to result.serverSeq) else emptyMap()
        is PushDecision.MergeAttribute -> buildMap {
            put(decision.fromId, null)
            decision.row.rowId?.let { put(it, result.serverSeq) }
        }
        PushDecision.DropLocal, PushDecision.Gone -> mapOf(result.id to null)
        is PushDecision.MovementKept, is PushDecision.MovementAuthor -> emptyMap()
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
