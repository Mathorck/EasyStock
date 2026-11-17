package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dto.sync.PushAnswer
import ch.mathorck.easystock.data.dto.sync.PushRequest
import ch.mathorck.easystock.data.model.article.ImageHash
import ch.mathorck.easystock.data.model.server.ServerUser
import ch.mathorck.easystock.data.model.sync.OutboxEntry
import ch.mathorck.easystock.data.model.sync.PendingPush
import ch.mathorck.easystock.data.model.sync.SyncState
import ch.mathorck.easystock.enums.sync.SyncTable
import kotlinx.serialization.json.JsonObject

/**
 * The phone side of a sync: the database (data/db/RoomSyncStore) and the photo files. Every write
 * method is one transaction, and does nothing when `sync_state` is gone (the phone left the server
 * while the cycle ran). Pulled and answered rows are written with `sync_state.applying` on, set and
 * cleared inside that same transaction, so they never fill the outbox.
 */
interface SyncStore {
    suspend fun syncState(): SyncState?

    suspend fun updateRole(role: String)

    suspend fun upsertUser(user: ServerUser)

    suspend fun pendingPush(): PendingPush?

    suspend fun savePendingPush(push: PendingPush)

    suspend fun clearPendingPush()

    /** Up to [limit] waiting changes, oldest first. */
    /** [table]: only the entries of that table (the limit applies after the filter). */
    suspend fun outbox(limit: Int, table: String? = null): List<OutboxEntry>

    /**
     * The current content of a row as a wire row, null when it doesn't exist. An article's photo is
     * given by its hash: when [hashNewImages], a photo without a known hash is hashed and recorded
     * (`image_hash`); otherwise its hash is unknown and the row can't equal a sent one.
     */
    suspend fun wireRow(table: SyncTable, id: String, hashNewImages: Boolean): JsonObject?

    /**
     * The `server_seq` of the server version of the row this phone holds or edited, sent as `baseSeq`
     * (first change wins). Null when the server never had the row, and for movements.
     */
    suspend fun serverSeq(table: SyncTable, id: String): Long?

    /** [serverSeq] of each of [ids] that has one, in one read where the store can. */
    suspend fun serverSeqs(table: SyncTable, ids: Collection<String>): Map<String, Long> =
        ids.mapNotNull { id -> serverSeq(table, id)?.let { id to it } }.toMap()

    /** The content of a photo with this hash, null when no such file is on the phone. */
    suspend fun imageBytes(sha256: String): ByteArray?

    /**
     * Writes the server's [answer] to the push [sent]: deletes, writes and rejected changes as
     * [decidePush] says, the `server_seq` of each row as [seqUpdates] says, then forgets the pending
     * push, in one transaction. Returns the decisions.
     */
    suspend fun applyPush(sent: PushRequest, answer: PushAnswer, now: Long): List<PushDecision>

    /** Which of [ids] exist in [table], soft-deleted rows included. */
    suspend fun existingIds(table: SyncTable, ids: Set<String>): Set<String>

    /**
     * Writes the [buffer] rows (and their `serverSeq`), users and alerts, recomputes `item.location_id` of the touched units,
     * and saves [next] as `sync_state.since` and [now] as the last sync, in one transaction.
     * [fullPull]: the pull started from 0 (after joining or a promotion). A movement already on the
     * phone only gets its author fields updated, in both cases. False when `sync_state` is gone (nothing written).
     * [localWins]: a row waiting in the outbox is skipped (it is pushed). False for a role that can't push:
     * the server's version is written over it and its outbox entry dropped.
     */
    suspend fun applyPull(buffer: PullBuffer, next: Long, fullPull: Boolean, now: Long, localWins: Boolean = true): Boolean

    /**
     * The server replaced its data set: deletes every synced row, the outbox, the pending push, the
     * rejected changes, alerts, the other users and photos, and sets `since` to 0, in one transaction (the photo
     * files after it). `sync_state` and the session stay. False when `sync_state` is gone.
     */
    suspend fun wipeServerData(): Boolean

    /** Photos of articles whose file is not on the phone yet. */
    suspend fun missingImages(): List<ImageHash>

    /** Stores a downloaded photo, unless the phone left the server meanwhile. */
    suspend fun saveImage(image: ImageHash, jpeg: ByteArray)

    /**
     * Deletes the photo files no article references any more (a pull replaced the photo), and their
     * `image_hash` rows. Keeps a photo the batch waiting to be sent still needs.
     */
    suspend fun removeOrphanPhotos() {}
}
