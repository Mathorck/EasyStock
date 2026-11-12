package ch.mathorck.easystock.push

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

/** A UnifiedPush distributor app installed on the phone (ntfy, NextPush…). [id]: its package name. */
data class PushDistributor(val id: String, val label: String)

/**
 * What the phone remembers about UnifiedPush, outside the database (it is not data, and a wipe forgets it).
 * [enabled]: the user chose to use [distributor]. [endpoint]: the last address the distributor gave.
 * [sentEndpoint] and [sentServer]: the address the server confirmed, and that server.
 * [failed]: the distributor refused the registration.
 */
@Serializable
data class PushRecord(
    val enabled: Boolean = false,
    val distributor: String? = null,
    val endpoint: String? = null,
    val sentEndpoint: String? = null,
    val sentServer: String? = null,
    val failed: Boolean = false,
)

/** True when the server of [serverUrl] doesn't have the current address yet. */
fun needsServerRegistration(record: PushRecord, serverUrl: String): Boolean =
    record.enabled && !record.failed && record.endpoint != null &&
        (record.sentEndpoint != record.endpoint || record.sentServer != serverUrl)

/** Where the push stands, as the Account screen shows it. */
sealed interface PushUiState {
    /** No distributor app installed: only the 15-minute background sync. */
    data object NoDistributor : PushUiState

    /** Push is off; the user can pick one of [distributors]. */
    data class Off(val distributors: List<PushDistributor>) : PushUiState

    data class On(val distributor: PushDistributor, val status: PushStatus) : PushUiState
}

enum class PushStatus {
    /** Waiting for the distributor's address or for the server to save it. */
    WAITING,

    /** The server has the address: it wakes the app. */
    ACTIVE,

    /** The distributor refused: the 15-minute background sync is used. */
    FAILED,
}

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

/** Where [PushRecord] is kept. */
interface PushStore {
    val record: StateFlow<PushRecord>

    suspend fun save(record: PushRecord)
}

/** The UnifiedPush connector library (Android), behind an interface. */
interface PushConnector {
    fun distributors(): List<PushDistributor>

    /** Saves [distributor] as the one to use and asks it for an address. */
    fun use(distributor: String)

    /** Asks the saved distributor again (it answers with the same or a new address). */
    fun register()

    fun unregister()
}

/** The server of the connection and its push calls. */
class PushTarget(val serverUrl: String, val api: PushApi)

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
