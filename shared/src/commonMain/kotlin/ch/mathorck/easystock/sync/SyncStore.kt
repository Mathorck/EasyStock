package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.model.ImageHash
import ch.mathorck.easystock.data.model.OutboxEntry
import ch.mathorck.easystock.data.model.PendingPush
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    suspend fun outbox(limit: Int): List<OutboxEntry>

    /**
     * The current content of a row as a wire row, null when it doesn't exist. An article's photo is
     * given by its hash: when [hashNewImages], a photo without a known hash is hashed and recorded
     * (`image_hash`); otherwise its hash is unknown and the row can't equal a sent one.
     */
    suspend fun wireRow(table: SyncTable, id: String, hashNewImages: Boolean): JsonObject?

    /** The content of a photo with this hash, null when no such file is on the phone. */
    suspend fun imageBytes(sha256: String): ByteArray?

    /**
     * Writes the server's [answer] to the push [sent]: deletes, writes and rejected changes as
     * [decidePush] says, then forgets the pending push, in one transaction. Returns the decisions.
     */
    suspend fun applyPush(sent: PushRequest, answer: PushAnswer, now: Long): List<PushDecision>

    /** Which of [ids] exist in [table], soft-deleted rows included. */
    suspend fun existingIds(table: SyncTable, ids: Set<String>): Set<String>

    /**
     * Writes the [buffer] rows, users and alerts, recomputes `item.location_id` of the touched units,
     * and saves [next] as `sync_state.since` and [now] as the last sync, in one transaction.
     * [fullPull]: the pull started from 0 (after joining or a promotion), movements may then be
     * updated. False when `sync_state` is gone (nothing written).
     */
    suspend fun applyPull(buffer: PullBuffer, next: Long, fullPull: Boolean, now: Long): Boolean

    /**
     * The server replaced its data set: deletes every synced row, the outbox, the pending push, the
     * rejected changes, alerts, users and photos, and sets `since` to 0, in one transaction (the photo
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

/**
 * One lock for everything that writes server data: the sync cycle, the photo downloads, and the wipe
 * of a logout, a revoked session or a reset. A wipe stops the running cycle first ([stopAndLock]), so
 * no pulled page is written after the data is erased.
 */
object SyncCoordinator {
    private val mutex = Mutex()

    @Volatile
    private var running: Job? = null

    /** Runs [block] alone, as the running sync job [job]. */
    internal suspend fun <T> cycle(job: Job?, block: suspend () -> T): T = mutex.withLock {
        running = job
        try {
            block()
        } finally {
            running = null
        }
    }

    /** Cancels the running cycle, then runs [block] alone. For the wipes. */
    suspend fun <T> stopAndLock(block: suspend () -> T): T {
        running?.cancel()
        return mutex.withLock { block() }
    }
}
