package ch.mathorck.easystock.server

import ch.mathorck.easystock.sync.SyncError
import ch.mathorck.easystock.sync.syncErrorOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkAccessTest {
    @Test
    fun privateAddressesAreLocal() {
        listOf(
            "http://10.0.2.2:8080",
            "http://10.255.255.255",
            "https://172.16.0.1",
            "https://172.31.255.1:8443/",
            "http://192.168.1.20:8080",
            "http://169.254.10.1",
            "http://[fd12:3456::1]:8080",
            "http://[fe80::1]",
            "http://nas.local:8080",
            "http://NAS.LOCAL.",
        ).forEach { assertTrue(isLocalNetworkAddress(it), it) }
    }

    @Test
    fun publicAddressesAndHostNamesAreNot() {
        listOf(
            "https://stock.example.org",
            "https://8.8.8.8",
            "http://172.15.0.1",
            "http://172.32.0.1",
            "http://192.169.1.1",
            "http://127.0.0.1:8080",
            "http://[2001:db8::1]",
            "http://[::1]",
            "http://nas.lan",
            "http://10.0.2",
            "http://10.0.2.256",
            "",
        ).forEach { assertFalse(isLocalNetworkAddress(it), it) }
    }

    @Test
    fun noAnswerWithoutTheLocalNetworkIsTheDeniedError() {
        assertEquals(ConnectError.LOCAL_NETWORK_DENIED, ConnectError.NETWORK.withLocalNetwork(true))
        assertEquals(ConnectError.NETWORK, ConnectError.NETWORK.withLocalNetwork(false))
        // An answer came: the network is fine
        assertEquals(ConnectError.WRONG_CREDENTIALS, ConnectError.WRONG_CREDENTIALS.withLocalNetwork(true))
        assertEquals(ConnectError.LOCAL_NETWORK_DENIED, connectErrorOf(ApiException(ApiError.NETWORK)).withLocalNetwork(true))
    }

    @Test
    fun aSyncWithoutAnswerAndWithoutTheLocalNetworkIsTheDeniedError() {
        assertEquals(SyncError.LOCAL_NETWORK_DENIED, syncErrorOf(ApiException(ApiError.NETWORK), localNetworkMissing = true))
        assertEquals(SyncError.NETWORK, syncErrorOf(ApiException(ApiError.NETWORK), localNetworkMissing = false))
        assertEquals(SyncError.SERVER, syncErrorOf(ApiException(ApiError.SERVER, 500), localNetworkMissing = true))
    }
}
