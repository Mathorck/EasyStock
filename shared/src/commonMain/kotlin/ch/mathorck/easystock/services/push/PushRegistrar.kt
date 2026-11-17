package ch.mathorck.easystock.services.push

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.data.dto.push.PushRecord
import ch.mathorck.easystock.enums.push.PushStatus
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** True when the server of [serverUrl] doesn't have the current address yet. */
fun needsServerRegistration(record: PushRecord, serverUrl: String): Boolean =
    record.enabled && !record.failed && record.endpoint != null &&
        (record.sentEndpoint != record.endpoint || record.sentServer != serverUrl)

/** The Account screen state from the installed [distributors] and the stored [record]. */
fun pushUiStateOf(distributors: List<PushDistributor>, record: PushRecord, serverUrl: String): PushUiState {
    val used = record.distributor?.let { id -> distributors.firstOrNull { it.id == id } }
    return when {
        record.enabled && used != null -> PushUiState.On(
            used,
            when {
                record.failed -> PushStatus.FAILED
                needsServerRegistration(record, serverUrl) || record.endpoint == null -> PushStatus.WAITING
                else -> PushStatus.ACTIVE
            },
        )
        distributors.isEmpty() -> PushUiState.NoDistributor
        else -> PushUiState.Off(distributors)
    }
}

/**
 * Keeps the server's UnifiedPush address in step with the distributor (02-app.md, "Push"). A push only
 * wakes the sync; its content is ignored. Never used in standalone mode: [target] answers null without
 * a `sync_state` row, and no call is made.
 */
class PushRegistrar(
    private val store: PushStore,
    private val connector: PushConnector,
    /** The connected server, null in standalone mode. */
    private val target: suspend () -> PushTarget?,
) {
    private val mutex = Mutex()

    val record: StateFlow<PushRecord> get() = store.record

    fun distributors(): List<PushDistributor> = connector.distributors()

    /** The user picked [distributor]. */
    suspend fun enable(distributor: String) = mutex.withLock {
        store.save(PushRecord(enabled = true, distributor = distributor))
        connector.use(distributor)
    }

    /** The user turned push off: the server forgets the address, then the distributor. */
    suspend fun disable() = mutex.withLock {
        val before = store.record.value
        store.save(PushRecord())
        connector.unregister()
        if (before.sentEndpoint != null) tellServer { it.api.unregister() }
    }

    /** The distributor gave [endpoint]: sent to the server. Dropped when push is off. */
    suspend fun onNewEndpoint(endpoint: String) = mutex.withLock {
        val record = store.record.value
        if (!record.enabled) {
            connector.unregister()
            return@withLock
        }
        store.save(record.copy(endpoint = endpoint, failed = false))
        sendIfNeeded()
    }

    suspend fun onRegistrationFailed() = mutex.withLock {
        val record = store.record.value
        if (record.enabled) store.save(record.copy(failed = true))
    }

    /** The distributor dropped the app (uninstalled, or the user removed it there). */
    suspend fun onUnregistered() = mutex.withLock {
        val before = store.record.value
        store.save(PushRecord())
        if (before.sentEndpoint != null) tellServer { it.api.unregister() }
    }

    /**
     * The app came to the front while connected: asks the distributor again (as the library advises, it
     * also finds a removed distributor), and sends the address again. The server upserts it: this brings
     * back an address it dropped (an endpoint that once answered 404, a restored server, a new session).
     */
    suspend fun refresh() = mutex.withLock {
        if (!store.record.value.enabled) return@withLock
        connector.register()
        sendIfNeeded(always = true)
    }

    /** Before a logout, while the session still works: the server forgets the address. */
    suspend fun beforeLogout() = mutex.withLock {
        val record = store.record.value
        if (record.sentEndpoint == null) return@withLock
        if (tellServer { it.api.unregister() }) store.save(record.copy(sentEndpoint = null, sentServer = null))
    }

    /** The wipe (logout, revoked session, reset): the distributor and the phone forget, no network. */
    suspend fun forget() = mutex.withLock {
        if (store.record.value != PushRecord()) {
            runCatching { connector.unregister() }
            store.save(PushRecord())
        }
    }

    private suspend fun sendIfNeeded(always: Boolean = false) {
        val target = target() ?: return
        val record = store.record.value
        if (!record.enabled || record.failed) return
        if (!always && !needsServerRegistration(record, target.serverUrl)) return
        val endpoint = record.endpoint ?: return
        if (tellServer(target) { it.api.register(endpoint) }) {
            store.save(store.record.value.copy(sentEndpoint = endpoint, sentServer = target.serverUrl))
        }
    }

    /** Best effort: false when the server can't be told (offline, error). Tried again at the next [refresh]. */
    private suspend fun tellServer(known: PushTarget? = null, call: suspend (PushTarget) -> Unit): Boolean {
        val target = known ?: target() ?: return false
        return try {
            withTimeoutOrNull(SERVER_TIMEOUT) { call(target) } != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        val SERVER_TIMEOUT = 10.seconds
    }
}

/** The process-wide registrar. Null where push is not available (iOS). */
@Composable
expect fun rememberPushRegistrar(): PushRegistrar?
