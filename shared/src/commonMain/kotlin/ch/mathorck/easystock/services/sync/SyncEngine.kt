package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dto.sync.PushRequest
import ch.mathorck.easystock.data.dto.sync.RefusedImages
import ch.mathorck.easystock.data.model.server.ServerUser
import ch.mathorck.easystock.data.model.sync.PendingPush
import ch.mathorck.easystock.data.model.sync.SyncState
import ch.mathorck.easystock.enums.auth.Action
import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.enums.sync.SyncError
import ch.mathorck.easystock.enums.sync.SyncResult
import ch.mathorck.easystock.enums.sync.SyncTable
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.exceptions.sync.DatasetEpochChanged
import ch.mathorck.easystock.exceptions.sync.LeftServer
import ch.mathorck.easystock.services.auth.Permissions
import ch.mathorck.easystock.services.auth.storedRoleOf
import ch.mathorck.easystock.services.server.AccountApi
import ch.mathorck.easystock.services.server.ProblemTypes
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The sync engine (02-app.md, "Sync engine"). One cycle at a time ([SyncCoordinator]):
 * 1. `GET /me`: refreshes the access token when needed (bearer plugin) and updates the role;
 * 2. push the outbox (Admin and Editor only), batch after batch;
 * 3. pull every page since `sync_state.since`, write the rows, users and alerts, then notify the new alerts
 *    (a full pull from 0 comes before the push, so the push carries the epoch it saved; for a role that
 *    can't push, the pulled rows replace the waiting ones);
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
    /** Called after each successful pull, in the cycle: shows the new low-stock alerts (alerts/AlertNotifier). */
    private val afterPull: suspend () -> Unit = {},
    /** Called right after a dataset-reset wipe: removes the alert notifications of the old data set. */
    private val onDatasetReset: () -> Unit = {},
    /** The data set this phone holds, sent with push and pull ([DatasetEpochStore]). */
    private val epochs: DatasetEpochStore = InMemoryDatasetEpochStore(),
    /**
     * Called before a dataset-reset wipe: writes a backup of the phone and returns its file name (null: none
     * written). An exception is a failed backup: the reset goes on anyway.
     */
    private val backupBeforeReset: suspend () -> String? = { null },
    /** Keeps the reset notice until the user dismisses it, also across a restart. */
    private val resetNotices: ResetNoticeStore = InMemoryResetNoticeStore(),
    /** True while the system blocks the local network for this app (Android 17+): a failed call is then blamed on it. */
    private val localNetworkMissing: () -> Boolean = { false },
    /** Keeps [refusedImages] across a restart. */
    private val refusedImageStore: RefusedImageStore = InMemoryRefusedImageStore(),
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _resetNotice = MutableStateFlow<DatasetResetNotice?>(null)

    /** Set when the server replaced its data set and the phone started over, until [resetNoticeShown]. */
    val resetNotice: StateFlow<DatasetResetNotice?> = _resetNotice.asStateFlow()

    fun resetNoticeShown() {
        _resetNotice.value = null
        scope.launch { resetNotices.save(null) }
    }

    /** Shows the notice a previous process kept and the user did not dismiss yet. */
    suspend fun restoreResetNotice() {
        val kept = DatasetResetNotice.decode(resetNotices.load()) ?: return
        _resetNotice.update { it ?: kept }
    }

    private var downloads: Job? = null

    /** The server that answered 426: paused until the app is updated (restarted) or another server is joined. Read and written in cycles only (one at a time). */
    private var pausedFor: String? = null

    /**
     * Photos the server refused, by hash, with the Problem `type` of the refusal: sent again at each cycle
     * (an Admin may raise the quota). Saved in [refusedImageStore], loaded at the first cycle of the server
     * [refusedImagesOf]. Read and written in cycles only.
     */
    private val refusedImages = LinkedHashMap<String, String?>()

    /** The server [refusedImages] belong to, null before they were loaded. */
    private var refusedImagesOf: String? = null

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

    /**
     * Forgets what this engine keeps of the server session: the refused photos (also their saved copy) and
     * the 426 pause. Called by the wipe of a logout, a revoked session or a deleted account
     * ([ch.mathorck.easystock.services.reset.DataReset]), under the [SyncCoordinator] lock: no cycle runs meanwhile.
     * Rejoining the same server then starts with an empty list.
     */
    suspend fun forgetServer() {
        pausedFor = null
        refusedImages.clear()
        refusedImagesOf = null
        saveRefusedImages()
        _status.value = SyncStatus()
    }

    private suspend fun cycle(): SyncResult {
        val state = store.syncState() ?: run {
            // Left the server: nothing of that session is shown any more
            pausedFor = null
            if (refusedImagesOf != null || refusedImages.isNotEmpty()) {
                refusedImages.clear()
                refusedImagesOf = null
                saveRefusedImages()
            }
            _status.value = SyncStatus()
            return SyncResult.NOT_CONNECTED
        }
        if (pausedFor == state.serverUrl) return SyncResult.PAUSED
        if (pausedFor != null) {
            pausedFor = null
            _status.value = SyncStatus()
        }
        _status.update { it.copy(running = true, fullPullRows = if (state.since == 0L) 0 else null) }
        loadRefusedImages(state.serverUrl)
        return try {
            val remote = remote(state)
            try {
                val role = refreshAccount(state, remote.account)
                val canPush = Permissions.can(Action.PUSH_CHANGES, connected = true, role = role)
                if (!canPush) {
                    // A batch sent before a demotion is never sent again (403): the pull brings what the server took
                    if (store.pendingPush() != null) store.clearPendingPush()
                    // The outbox can't be pushed any more: the server's rows are written over the waiting ones
                    pull(remote.sync, state.since, if (state.since > 0) epochs.load() else null, localWins = false)
                    // A Viewer raises flags: only those are sent (the server refuses any other row of that role),
                    // after the pull so a reset or a new epoch is seen first
                    if (Permissions.can(Action.FLAG_ARTICLE, connected = true, role = role)) push(remote.sync, epochs.load(), flagsOnly = true)
                } else if (state.since == 0L) {
                    // No data set yet (joined, promoted): the full pull comes first and saves its epoch, so
                    // the push carries it and a reset of the server in between is seen (409), never pushed into
                    pull(remote.sync, 0, null)
                    push(remote.sync, epochs.load())
                } else {
                    // The epoch is only sent for data pulled with it
                    val epoch = epochs.load()
                    // Before anything is pushed (photos first): a reset is seen here, not only by the 409 of the push.
                    // Nothing to push: the pull carries the epoch and sees the reset alone, no extra call
                    if (epoch != null && hasSomethingToPush()) checkServerEpoch(remote.sync, epoch)
                    push(remote.sync, epoch)
                    pull(remote.sync, state.since, epoch)
                }
            } catch (e: Exception) {
                if (!isDatasetReset(e)) throw e
                // Once per cycle: a second reset fails the cycle (SERVER), never wipes again
                startOver(remote.sync)
            }
            notifyAlerts()
            _status.update { it.copy(error = null, problemType = null) }
            // A failed cleanup is not a failed sync: the next cycle tries again
            try {
                store.removeOrphanPhotos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            startDownloads(remote.sync)
            SyncResult.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: LeftServer) {
            SyncResult.NOT_CONNECTED
        } catch (e: ApiException) {
            val error = syncErrorOf(e, localNetworkMissing())
            if (error == SyncError.APP_TOO_OLD) pausedFor = state.serverUrl
            _status.update { it.copy(error = error, problemType = e.problemType, paused = error == SyncError.APP_TOO_OLD) }
            if (error == SyncError.APP_TOO_OLD) SyncResult.PAUSED else SyncResult.FAILED
        } catch (e: Exception) {
            // A row the database refused (a reference still missing after the last page...): tried again next time
            _status.update { it.copy(error = SyncError.SERVER, problemType = null) }
            SyncResult.FAILED
        } finally {
            _status.update { it.copy(running = false, fullPullRows = null) }
        }
    }

    /**
     * Compares the epoch of `GET /server-info` with the one this phone holds: another one means the
     * server replaced its data set ([DatasetEpochChanged], handled like the 409 `dataset-reset`). A server
     * that doesn't answer or sends no epoch is left to the sync calls, which carry the header too.
     */
    private suspend fun checkServerEpoch(api: SyncApi, held: String) {
        val answered = try {
            api.serverInfo().datasetEpoch
        } catch (e: ApiException) {
            return
        }
        if (answered.isNotBlank() && answered != held) throw DatasetEpochChanged()
    }

    /** True when [push] will send something: a batch waiting for its answer, a local change or a refused photo. */
    private suspend fun hasSomethingToPush(): Boolean =
        refusedImages.isNotEmpty() || store.pendingPush() != null || store.outbox(1).isNotEmpty()

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
        // Everything on the phone is about to go: keep a copy first. A failure does not stop the reset
        var backupFile: String? = null
        var backupFailed = false
        try {
            backupFile = backupBeforeReset()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            backupFailed = true
        }
        if (!store.wipeServerData()) throw LeftServer()
        // The alerts of the old data set are gone: the pull from 0 brings the current ones, as after a join
        try {
            onDatasetReset()
        } catch (e: Exception) {
        }
        epochs.save(null)
        // The photos of the old data set are gone with it
        refusedImages.clear()
        saveRefusedImages()
        _status.update { it.copy(imagesNotSent = 0, imageProblemType = null) }
        val notice = DatasetResetNotice((_resetNotice.value?.lostChanges ?: 0) + lost, backupFile ?: _resetNotice.value?.backupFile, backupFailed)
        _resetNotice.value = notice
        // On disk before the pull: the process may die before the user sees it
        try {
            resetNotices.save(notice.encode())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
        _status.update { it.copy(fullPullRows = 0) }
        pull(api, 0, null)
    }

    private suspend fun push(api: SyncApi, epoch: String?, flagsOnly: Boolean = false) {
        // Photos refused before: their rows are on the server already
        if (refusedImages.isNotEmpty()) uploadImages(api, refusedImages.keys.toList())
        repeat(MAX_BATCHES_PER_CYCLE) {
            val pending = store.pendingPush() ?: buildBatch(api, flagsOnly) ?: return
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
    private suspend fun buildBatch(api: SyncApi, flagsOnly: Boolean): PendingPush? {
        // The table filter comes before the limit: a backlog of other rows can't hide the flags
        val entries = store.outbox(PUSH_LIMIT, if (flagsOnly) SyncTable.ARTICLE_FLAG.tableName else null)
        if (entries.isEmpty()) return null
        val byTable = entries.groupBy { it.tableName }
        val rows = LinkedHashMap<String, List<JsonObject>>()
        SyncTable.entries.forEach { table ->
            // Each row once: the server takes a second copy as a stale edit of the first (the outbox key already ensures it)
            val ids = byTable[table.tableName].orEmpty().map { it.rowId }.distinct()
            // An edit of a row the server has carries the seq of the version it is based on (first change wins)
            val seqs = if (ids.isEmpty()) emptyMap() else store.serverSeqs(table, ids)
            val list = ids.mapNotNull { id -> store.wireRow(table, id, hashNewImages = true)?.withBaseSeq(seqs[id]) }
            if (list.isNotEmpty()) rows[table.tableName] = list
        }
        // Only entries of rows that no longer exist (never the case: hard deletes clear their entry)
        if (rows.isEmpty()) return null
        uploadImages(api, rows[SyncTable.ARTICLE.tableName].orEmpty().mapNotNull { it.imageSha256 }.distinct())
        val push = PendingPush(batchId = newBatchId(), body = "", createdAt = clock.now().toEpochMilliseconds())
        val withBody = push.copy(body = encodePush(PushRequest(push.batchId, rows)))
        store.savePendingPush(withBody)
        return withBody
    }

    /**
     * Uploads the photos the server doesn't have yet, one by one. A photo the server refuses (quota, too
     * large, not a JPEG...) never blocks the rows: the server takes an article whose photo is missing (no
     * reference check on `image_sha256`; the other phones show the placeholder), the photo is kept in
     * [refusedImages] and shown as a warning. No answer, a revoked session, a too old app, a rate limit or a
     * server error fail the cycle as before: the batch is sent later with its photos.
     */
    private suspend fun uploadImages(api: SyncApi, shas: List<String>) {
        if (shas.isEmpty()) return
        val before = LinkedHashMap(refusedImages)
        try {
            uploadEach(api, shas)
        } finally {
            // Also when the cycle fails half-way: the refusals seen so far are kept
            if (refusedImages != before) saveRefusedImages()
        }
        _status.update {
            it.copy(imagesNotSent = refusedImages.size, imageProblemType = refusedImages.values.lastOrNull { type -> type != null })
        }
    }

    private suspend fun uploadEach(api: SyncApi, shas: List<String>) {
        for (sha in shas) {
            try {
                if (!api.hasImage(sha)) {
                    // The photo was replaced meanwhile: nothing left to send
                    val bytes = store.imageBytes(sha)
                    if (bytes == null) {
                        refusedImages.remove(sha)
                        continue
                    }
                    api.putImage(sha, bytes)
                }
                refusedImages.remove(sha)
            } catch (e: ApiException) {
                if (!isImageRefusal(e)) throw e
                refusedImages.remove(sha)
                refusedImages[sha] = e.problemType
            }
        }
    }

    /** Loads the photos refused by [serverUrl] in an earlier process, once per server. A store failure loses them only. */
    private suspend fun loadRefusedImages(serverUrl: String) {
        if (refusedImagesOf == serverUrl) return
        refusedImages.clear()
        refusedImagesOf = serverUrl
        val kept = try {
            RefusedImages.decode(refusedImageStore.load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // Another server's photos are never sent here
        if (kept?.serverUrl == serverUrl) refusedImages.putAll(kept.images)
        _status.update {
            it.copy(imagesNotSent = refusedImages.size, imageProblemType = refusedImages.values.lastOrNull { type -> type != null })
        }
    }

    /** A failure only loses the list after a restart: the photos are then sent with their next change. */
    private suspend fun saveRefusedImages() {
        val server = refusedImagesOf
        val value = if (refusedImages.isEmpty() || server == null) null else RefusedImages(server, LinkedHashMap(refusedImages)).encode()
        try {
            refusedImageStore.save(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    /** [startEpoch]: the data set of the rows on the phone, null when unknown or for a full pull. */
    /** [localWins]: false when the role can't push, see [SyncStore.applyPull]. */
    private suspend fun pull(api: SyncApi, startSince: Long, startEpoch: String?, localWins: Boolean = true) {
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
                if (!store.applyPull(buffer, since, fullPull, now, localWins)) throw LeftServer()
                written += buffer.rowCount
                buffer.clear()
                if (fullPull) _status.update { it.copy(fullPullRows = written) }
            }
            if (!page.hasMore) return
        }
    }

    /** A notification that fails never fails the sync: the alerts stay pending for the next cycle. */
    private suspend fun notifyAlerts() {
        try {
            afterPull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tried again after the next pull
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

/** The server replaced its data set since this phone pulled it. */
internal fun isDatasetReset(e: Exception): Boolean =
    e is DatasetEpochChanged || (e is ApiException && e.problemType == ProblemTypes.DATASET_RESET)

/** A refusal of this one photo (413, 415, 422, 404, 409): the rows are pushed without it. Anything else fails the cycle. */
internal fun isImageRefusal(e: ApiException): Boolean =
    e.error == ApiError.INVALID || e.error == ApiError.NOT_FOUND || e.error == ApiError.CONFLICT

internal fun syncErrorOf(e: ApiException, localNetworkMissing: Boolean = false): SyncError = when (e.error) {
    ApiError.NETWORK -> if (localNetworkMissing) SyncError.LOCAL_NETWORK_DENIED else SyncError.NETWORK
    ApiError.APP_TOO_OLD -> SyncError.APP_TOO_OLD
    ApiError.SESSION_REVOKED -> SyncError.SESSION_REVOKED
    ApiError.FORBIDDEN -> SyncError.FORBIDDEN
    else -> SyncError.SERVER
}
