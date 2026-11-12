package ch.mathorck.easystock.push

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryPushStore(initial: PushRecord = PushRecord()) : PushStore {
    private val _record = MutableStateFlow(initial)
    override val record: StateFlow<PushRecord> = _record

    override suspend fun save(record: PushRecord) {
        _record.value = record
    }
}

private class FakeConnector(var installed: List<PushDistributor> = listOf(NTFY)) : PushConnector {
    val calls = mutableListOf<String>()

    override fun distributors() = installed

    override fun use(distributor: String) {
        calls += "use:$distributor"
    }

    override fun register() {
        calls += "register"
    }

    override fun unregister() {
        calls += "unregister"
    }
}

private class FakePushApi : PushApi {
    val calls = mutableListOf<String>()
    var fail = false
    var hang = false

    override suspend fun register(endpoint: String) {
        if (hang) awaitCancellation()
        if (fail) error("offline")
        calls += "register:$endpoint"
    }

    override suspend fun unregister() {
        if (fail) error("offline")
        calls += "unregister"
    }
}

private val NTFY = PushDistributor("io.heckel.ntfy", "ntfy")
private const val SERVER = "http://192.168.1.20:8080"
private const val ENDPOINT = "https://ntfy.example.org/upAbc"

class PushRegistrarTest {
    private val store = MemoryPushStore()
    private val connector = FakeConnector()
    private val api = FakePushApi()
    private var connected = true
    private var serverUrl = SERVER
    private val registrar = PushRegistrar(store, connector) { if (connected) PushTarget(serverUrl, api) else null }

    private suspend fun registered() {
        registrar.enable(NTFY.id)
        registrar.onNewEndpoint(ENDPOINT)
    }

    // ---------- needsServerRegistration / pushUiStateOf ----------

    @Test
    fun registrationIsNeededForANewEndpointOrAnotherServer() {
        val sent = PushRecord(enabled = true, distributor = NTFY.id, endpoint = ENDPOINT, sentEndpoint = ENDPOINT, sentServer = SERVER)
        assertFalse(needsServerRegistration(sent, SERVER))
        assertTrue(needsServerRegistration(sent.copy(endpoint = "https://ntfy.example.org/upNew"), SERVER))
        assertTrue(needsServerRegistration(sent, "https://other.example.org"))
        assertTrue(needsServerRegistration(sent.copy(sentEndpoint = null), SERVER))
        assertFalse(needsServerRegistration(sent.copy(enabled = false, sentEndpoint = null), SERVER))
        assertFalse(needsServerRegistration(sent.copy(endpoint = null, sentEndpoint = null), SERVER))
        assertFalse(needsServerRegistration(sent.copy(failed = true, sentEndpoint = null), SERVER))
    }

    @Test
    fun uiStateWithoutDistributorSaysTheBackgroundSyncIsUsed() {
        assertEquals(PushUiState.NoDistributor, pushUiStateOf(emptyList(), PushRecord(), SERVER))
        // Chosen, then uninstalled
        assertEquals(PushUiState.NoDistributor, pushUiStateOf(emptyList(), PushRecord(enabled = true, distributor = NTFY.id), SERVER))
    }

    @Test
    fun uiStateOffListsTheInstalledDistributors() {
        assertEquals(PushUiState.Off(listOf(NTFY)), pushUiStateOf(listOf(NTFY), PushRecord(), SERVER))
    }

    @Test
    fun uiStateOnFollowsTheRegistration() {
        val chosen = PushRecord(enabled = true, distributor = NTFY.id)
        assertEquals(PushUiState.On(NTFY, PushStatus.WAITING), pushUiStateOf(listOf(NTFY), chosen, SERVER))
        val endpoint = chosen.copy(endpoint = ENDPOINT)
        assertEquals(PushUiState.On(NTFY, PushStatus.WAITING), pushUiStateOf(listOf(NTFY), endpoint, SERVER))
        val sent = endpoint.copy(sentEndpoint = ENDPOINT, sentServer = SERVER)
        assertEquals(PushUiState.On(NTFY, PushStatus.ACTIVE), pushUiStateOf(listOf(NTFY), sent, SERVER))
        assertEquals(PushUiState.On(NTFY, PushStatus.FAILED), pushUiStateOf(listOf(NTFY), chosen.copy(failed = true), SERVER))
    }

    // ---------- PushRegistrar ----------

    @Test
    fun enablingUsesThePickedDistributorAndSendsItsEndpoint() = runTest {
        registrar.enable(NTFY.id)
        assertEquals(listOf("use:${NTFY.id}"), connector.calls)
        assertTrue(api.calls.isEmpty())
        registrar.onNewEndpoint(ENDPOINT)
        assertEquals(listOf("register:$ENDPOINT"), api.calls)
        assertEquals(PushRecord(true, NTFY.id, ENDPOINT, ENDPOINT, SERVER), store.record.value)
    }

    @Test
    fun theSameEndpointFromTheDistributorIsNotSentTwice() = runTest {
        registered()
        registrar.onNewEndpoint(ENDPOINT)
        assertEquals(listOf("register:$ENDPOINT"), api.calls)
    }

    @Test
    fun eachRefreshSendsTheAddressAgain() = runTest {
        // The server may have dropped it: it upserts
        registered()
        registrar.refresh()
        assertEquals(listOf("register:$ENDPOINT", "register:$ENDPOINT"), api.calls)
        assertEquals(listOf("use:${NTFY.id}", "register"), connector.calls)
    }

    @Test
    fun refreshSendsNothingAfterARefusal() = runTest {
        registered()
        registrar.onRegistrationFailed()
        registrar.refresh()
        assertEquals(listOf("register:$ENDPOINT"), api.calls)
    }

    @Test
    fun aFailedSendIsRetriedAtTheNextRefresh() = runTest {
        registrar.enable(NTFY.id)
        api.fail = true
        registrar.onNewEndpoint(ENDPOINT)
        assertNull(store.record.value.sentEndpoint)
        api.fail = false
        registrar.refresh()
        assertEquals(listOf("register:$ENDPOINT"), api.calls)
        assertEquals(ENDPOINT, store.record.value.sentEndpoint)
    }

    @Test
    fun aServerThatNeverAnswersTimesOut() = runTest {
        registrar.enable(NTFY.id)
        api.hang = true
        registrar.onNewEndpoint(ENDPOINT)
        assertNull(store.record.value.sentEndpoint)
    }

    @Test
    fun standaloneMakesNoCall() = runTest {
        connected = false
        registered()
        registrar.refresh()
        assertTrue(api.calls.isEmpty())
        assertNull(store.record.value.sentEndpoint)
    }

    @Test
    fun anEndpointWhilePushIsOffIsDropped() = runTest {
        registrar.onNewEndpoint(ENDPOINT)
        assertEquals(listOf("unregister"), connector.calls)
        assertTrue(api.calls.isEmpty())
        assertEquals(PushRecord(), store.record.value)
    }

    @Test
    fun refreshDoesNothingWhilePushIsOff() = runTest {
        registrar.refresh()
        assertTrue(connector.calls.isEmpty())
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun disablingTellsTheServerAndTheDistributor() = runTest {
        registered()
        registrar.disable()
        assertEquals(listOf("register:$ENDPOINT", "unregister"), api.calls)
        assertEquals("unregister", connector.calls.last())
        assertEquals(PushRecord(), store.record.value)
    }

    @Test
    fun disablingOfflineStillTurnsPushOff() = runTest {
        registered()
        api.fail = true
        registrar.disable()
        assertEquals(PushRecord(), store.record.value)
        assertEquals("unregister", connector.calls.last())
    }

    @Test
    fun theDistributorDroppingTheAppUnregistersOnTheServer() = runTest {
        registered()
        registrar.onUnregistered()
        assertEquals(listOf("register:$ENDPOINT", "unregister"), api.calls)
        // Already unregistered by the distributor: not asked again
        assertEquals(listOf("use:${NTFY.id}"), connector.calls)
        assertEquals(PushRecord(), store.record.value)
    }

    @Test
    fun aRefusedRegistrationIsShown() = runTest {
        registrar.enable(NTFY.id)
        registrar.onRegistrationFailed()
        assertTrue(store.record.value.failed)
        assertEquals(PushUiState.On(NTFY, PushStatus.FAILED), pushUiStateOf(listOf(NTFY), store.record.value, SERVER))
        // A new endpoint clears the failure
        registrar.onNewEndpoint(ENDPOINT)
        assertFalse(store.record.value.failed)
        assertEquals(ENDPOINT, store.record.value.sentEndpoint)
    }

    @Test
    fun beforeLogoutUnregistersOnTheServer() = runTest {
        registered()
        registrar.beforeLogout()
        assertEquals(listOf("register:$ENDPOINT", "unregister"), api.calls)
        assertNull(store.record.value.sentEndpoint)
        // Nothing sent: nothing to tell
        registrar.beforeLogout()
        assertEquals(2, api.calls.size)
    }

    @Test
    fun forgetUnregistersLocallyWithoutNetwork() = runTest {
        registered()
        registrar.forget()
        assertEquals(listOf("register:$ENDPOINT"), api.calls)
        assertEquals("unregister", connector.calls.last())
        assertEquals(PushRecord(), store.record.value)
    }

    @Test
    fun forgetInStandaloneTouchesNothing() = runTest {
        registrar.forget()
        assertTrue(connector.calls.isEmpty())
    }

    @Test
    fun anotherServerGetsTheEndpointAgain() = runTest {
        registered()
        serverUrl = "https://other.example.org"
        registrar.refresh()
        assertEquals(listOf("register:$ENDPOINT", "register:$ENDPOINT"), api.calls)
        assertEquals("https://other.example.org", store.record.value.sentServer)
    }
}
