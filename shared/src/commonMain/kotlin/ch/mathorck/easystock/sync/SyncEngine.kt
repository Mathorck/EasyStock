package ch.mathorck.easystock.sync

import ch.mathorck.easystock.auth.Action
import ch.mathorck.easystock.auth.Permissions
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.auth.storedRoleOf
import ch.mathorck.easystock.data.model.PendingPush
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.server.AccountApi
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.ProblemTypes
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** The server calls of one connection. */
class SyncRemote(val sync: SyncApi, val account: AccountApi)

/** Why the last cycle failed, as the Account screen shows it. */
enum class SyncError {
    /** No answer: offline or server down. Retried at the next trigger. */
    NETWORK,

    /** 426: the server needs a newer app. Sync is paused until the app is updated (restarted). */
    APP_TOO_OLD,

    /** The session was revoked: the phone is wiped (auth/ServerSessionEvents). */
    SESSION_REVOKED,

    /** 403 on a push: the role can't send changes any more. */
    FORBIDDEN,

    /** Any other refusal or an answer the app can't read. */
    SERVER,
}

/** State of the sync engine, for the UI. */
data class SyncStatus(
    val running: Boolean = false,
    /** Rows written so far by the running full pull (after joining or a promotion), null otherwise. */
    val fullPullRows: Int? = null,
    val error: SyncError? = null,
    /** A 426 paused the sync until the app is updated. */
    val paused: Boolean = false,
)

/** How a cycle ended. */
enum class SyncResult { DONE, NOT_CONNECTED, PAUSED, FAILED }

/**
 * The sync engine (02-app.md, "Sync engine"). One cycle at a time ([SyncCoordinator]):
 * 1. `GET /me`: refreshes the access token when needed (bearer plugin) and updates the role;
 * 2. push the outbox (Admin and Editor only), batch after batch;
 * 3. pull every page since `sync_state.since`, write the rows, users and alerts;
 * 4. download the missing photos, in the background.
 * Never used in standalone mode: no `sync_state` row, no cycle, no network.
 */
@OptIn(ExperimentalUuidApi::class)
class SyncEngine(
    private val store: SyncStore,
    /** The server calls of the connection of [SyncState]. */
    private val remote: suspend (SyncState) -> SyncRemote,
    /** For the photo downloads, which outlive a cycle. */
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val newBatchId: () -> String = { Uuid.random().toString() },
    /** Called after photos were downloaded, so the screens load them. */
    private val onImagesDownloaded: () -> Unit = {},
    /** The data set this phone holds, sent with push and pull ([DatasetEpochStore]). */
    private val epochs: DatasetEpochStore = InMemoryDatasetEpochStore(),
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _resetNotice = MutableStateFlow<DatasetResetNotice?>(null)

    /** Set when the server replaced its data set and the phone started over, until [resetNoticeShown]. */
    val resetNotice: StateFlow<DatasetResetNotice?> = _resetNotice.asStateFlow()

    fun resetNoticeShown() {
        _resetNotice.value = null
    }

    private var downloads: Job? = null

    /** The server that answered 426: paused until the app is updated (restarted) or another server is joined. Read and written in cycles only (one at a time). */
    private var pausedFor: String? = null

    /**
     * Runs one cycle now, after the running one if any. The cycle runs in the engine scope: a wipe
     * cancels it ([SyncCoordinator.stopAndLock]), never the caller.
     */
    suspend fun sync(): SyncResult {
        val cycle = scope.async { SyncCoordinator.cycle(coroutineContext[Job]) { cycle() } }
        return try {
            cycle.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            // Stopped by a wipe: the phone left the server
            SyncResult.NOT_CONNECTED
        }
    }

    /** Starts a cycle in the background. */
    fun requestSync() {
        scope.launch { sync() }
    }

    private suspend fun cycle(): SyncResult {
        val state = store.syncState() ?: run {
            // Left the server: nothing of that session is shown any more
            pausedFor = null
            _status.value = SyncStatus()
            return SyncResult.NOT_CONNECTED
        }
        if (pausedFor == state.serverUrl) return SyncResult.PAUSED
        if (pausedFor != null) {
            pausedFor = null
            _status.value = SyncStatus()
        }
        _status.update { it.copy(running = true, fullPullRows = if (state.since == 0L) 0 else null) }
        return try {
            val remote = remote(state)
            try {
                val role = refreshAccount(state, remote.account)
                // The epoch is only sent for data pulled with it: a full pull starts a data set
                val epoch = if (state.since > 0) epochs.load() else null
                if (Permissions.can(Action.PUSH_CHANGES, connected = true, role = role)) push(remote.sync, epoch)
                pull(remote.sync, state.since, epoch)
            } catch (e: Exception) {
                if (!isDatasetReset(e)) throw e
                // Once per cycle: a second reset fails the cycle (SERVER), never wipes again
                startOver(remote.sync)
            }
            _status.update { it.copy(error = null) }
            startDownloads(remote.sync)
            SyncResult.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: LeftServer) {
            SyncResult.NOT_CONNECTED
        } catch (e: ApiException) {
            val error = syncErrorOf(e)
            if (error == SyncError.APP_TOO_OLD) pausedFor = state.serverUrl
            _status.update { it.copy(error = error, paused = error == SyncError.APP_TOO_OLD) }
            if (error == SyncError.APP_TOO_OLD) SyncResult.PAUSED else SyncResult.FAILED
        } catch (e: Exception) {
            // A row the database refused (a reference still missing after the last page...): tried again next time
            _status.update { it.copy(error = SyncError.SERVER) }
            SyncResult.FAILED
        } finally {
            _status.update { it.copy(running = false, fullPullRows = null) }
        }
    }

    /** `GET /me`: the role may have changed since the last cycle (an Admin changed it). */
    private suspend fun refreshAccount(state: SyncState, account: AccountApi): Role? {
        val me = account.me()
        val role = storedRoleOf(me.role)
        if (role != state.role) store.updateRole(role)
        if (me.id.isNotEmpty() && me.displayName.isNotEmpty()) store.upsertUser(ServerUser(me.id, me.displayName))
        return Role.fromDb(role)
    }

    /**
     * The server replaced its data set (409 `dataset-reset`, or a pull answer with another epoch):
     * the unsent changes are counted (lost), every server row and photo of this phone is erased, and
     * everything is pulled again from 0. The session stays connected.
     */
    private suspend fun startOver(api: SyncApi) {
        // A download would store a photo of the old data set
        downloads?.cancelAndJoin()
        val lost = store.outbox(Int.MAX_VALUE).size
        if (!store.wipeServerData()) throw LeftServer()
        epochs.save(null)
        _resetNotice.update { DatasetResetNotice((it?.lostChanges ?: 0) + lost) }
        _status.update { it.copy(fullPullRows = 0) }
        pull(api, 0, null)
    }

    private suspend fun push(api: SyncApi, epoch: String?) {
        repeat(MAX_BATCHES_PER_CYCLE) {
            val pending = store.pendingPush() ?: buildBatch(api) ?: return
            val answer = try {
                api.push(pending.body, epoch)
            } catch (e: ApiException) {
                // Network: keep the batch, the next cycle sends the same id and body again.
                // Refused as a whole (403 role, 409 batch id, 413...): the server applied nothing,
                // so the batch is rebuilt from the outbox next time, with a new id
                if (e.error != ApiError.NETWORK && e.error != ApiError.SESSION_REVOKED && e.error != ApiError.APP_TOO_OLD) {
                    store.clearPendingPush()
                }
                throw e
            }
            val decisions = store.applyPush(decodePush(pending.body), answer, clock.now().toEpochMilliseconds())
            // Only rows changed again meanwhile are left: they go in the next cycle
            if (decisions.isEmpty() || decisions.all { it == PushDecision.Keep }) return
        }
    }

    /** Reads the outbox, uploads the new photos, and saves the batch before it is sent. Null when nothing waits. */
    private suspend fun buildBatch(api: SyncApi): PendingPush? {
        val entries = store.outbox(PUSH_LIMIT)
        if (entries.isEmpty()) return null
        val byTable = entries.groupBy { it.tableName }
        val rows = LinkedHashMap<String, List<JsonObject>>()
        SyncTable.entries.forEach { table ->
            val list = byTable[table.tableName].orEmpty().mapNotNull { store.wireRow(table, it.rowId, hashNewImages = true) }
            if (list.isNotEmpty()) rows[table.tableName] = list
        }
        // Only entries of rows that no longer exist (never the case: hard deletes clear their entry)
        if (rows.isEmpty()) return null
        rows[SyncTable.ARTICLE.tableName].orEmpty().mapNotNull { it.imageSha256 }.distinct().forEach { sha ->
            if (!api.hasImage(sha)) store.imageBytes(sha)?.let { api.putImage(sha, it) }
        }
        val push = PendingPush(batchId = newBatchId(), body = "", createdAt = clock.now().toEpochMilliseconds())
        val withBody = push.copy(body = encodePush(PushRequest(push.batchId, rows)))
        store.savePendingPush(withBody)
        return withBody
    }

    /** [startEpoch]: the data set of the rows on the phone, null when unknown or for a full pull. */
    private suspend fun pull(api: SyncApi, startSince: Long, startEpoch: String?) {
        val fullPull = startSince == 0L
        val buffer = PullBuffer()
        var since = startSince
        var epoch = startEpoch
        var written = 0
        while (true) {
            val page = api.pull(since, epoch = epoch)
            page.datasetEpoch?.let { answered ->
                // A server that doesn't check the header: the answer still tells
                if (epoch != null && answered != epoch) throw DatasetEpochChanged()
                // Saved before the rows: a crash in between leaves `since` at its old value
                if (epoch == null) epochs.save(answered)
                epoch = answered
            }
            buffer.add(page)
            since = page.next
            val missing = buffer.missingReferences { table, ids -> store.existingIds(table, ids) }
            // Rows pointing to a parent of a later page wait for it; the last page is written anyway
            if (missing.isEmpty() || !page.hasMore) {
                val now = clock.now().toEpochMilliseconds()
                if (!store.applyPull(buffer, since, fullPull, now)) throw LeftServer()
                written += buffer.rowCount
                buffer.clear()
                if (fullPull) _status.update { it.copy(fullPullRows = written) }
            }
            if (!page.hasMore) return
        }
    }

    /** Downloads the photos still missing, one job at a time, outside the cycle (it can take long). */
    private fun startDownloads(api: SyncApi) {
        if (downloads?.isActive == true) return
        downloads = scope.launch {
            val missing = runCatching { store.missingImages() }.getOrDefault(emptyList())
            var any = false
            for (image in missing) {
                val bytes = try {
                    api.getImage(image.sha256)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    if (e.error == ApiError.NETWORK) break else null
                } ?: continue
                if (sha256Hex(bytes) != image.sha256) continue
                SyncCoordinator.cycle(null) { store.saveImage(image, bytes) }
                any = true
            }
            if (any) onImagesDownloaded()
        }
    }

    /** Waits for the running photo downloads (tests). */
    internal suspend fun awaitDownloads() {
        downloads?.join()
    }

    companion object {
        /** Outbox rows per push (02-app.md). */
        const val PUSH_LIMIT = 500

        /** Batches sent in one cycle at most, so a cycle always ends. */
        const val MAX_BATCHES_PER_CYCLE = 20
    }
}

/** `sync_state` is gone: the phone left the server while the cycle ran. */
private class LeftServer : Exception()

/** The server replaced its data set since this phone pulled it. */
internal fun isDatasetReset(e: Exception): Boolean =
    e is DatasetEpochChanged || (e is ApiException && e.problemType == ProblemTypes.DATASET_RESET)

internal fun syncErrorOf(e: ApiException): SyncError = when (e.error) {
    ApiError.NETWORK -> SyncError.NETWORK
    ApiError.APP_TOO_OLD -> SyncError.APP_TOO_OLD
    ApiError.SESSION_REVOKED -> SyncError.SESSION_REVOKED
    ApiError.FORBIDDEN -> SyncError.FORBIDDEN
    else -> SyncError.SERVER
}
