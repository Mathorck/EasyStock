package ch.mathorck.easystock.services.sync

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
     * Movements are append-only, on the phone too: the local movement ([phone]) is never overwritten.
     * Only its author fields are set; when [server] differs in another column, both versions are
     * recorded in a `rejected_change` (sync/MovementDiffers).
     */
    data class MovementKept(val server: JsonObject, val phone: JsonObject) : PushDecision

    /**
     * A stale edit (reason `conflict`, first change wins): another phone changed the row first. Write
     * [server] (keep theirs, the default) and record the phone's version [phone] with the server's [seq]
     * in a `rejected_change`, until the user chooses (sync/RowConflict): "Keep mine" sends [phone] again
     * with `baseSeq` = [seq].
     */
    data class Conflict(val server: JsonObject, val phone: JsonObject, val seq: Long) : PushDecision

    /** The row was changed again after it was read: keep the outbox entry, write nothing. The newer change goes in the next push. */
    data object Keep : PushDecision

    /**
     * An accepted movement came back with its stored [row]: set only its author fields (`created_by`,
     * `device_id`, chosen by the server) on the local movement, then delete the outbox entry.
     */
    data class MovementAuthor(val row: JsonObject) : PushDecision

    /** The row no longer exists locally: forget the outbox entry. */
    data object Gone : PushDecision
}
