package ch.mathorck.easystock.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServerAddressTest {
    @Test
    fun withoutSchemeHttpsIsTriedFirst() {
        val address = assertIs<TypedAddress.Url>(parseTypedAddress("  192.168.1.20:8080  "))
        assertEquals("https://192.168.1.20:8080", address.url)
        assertEquals("http://192.168.1.20:8080", address.httpFallback)
        assertEquals(TypedAddress.Url("https://stock.example.org", false), parseTypedAddress("stock.example.org/"))
    }

    @Test
    fun aTypedSchemeIsKept() {
        val http = assertIs<TypedAddress.Url>(parseTypedAddress("HTTP://nas.local:8080/easystock/"))
        assertEquals("http://nas.local:8080/easystock", http.url)
        assertNull(http.httpFallback)
        val https = assertIs<TypedAddress.Url>(parseTypedAddress("https://[fd00::1]:8443"))
        assertEquals("https://[fd00::1]:8443", https.url)
        assertNull(https.httpFallback)
    }

    @Test
    fun invalidAddresses() {
        listOf("", "   ", "ftp://host", "host:99999", "host:abc", "user@host", "host?x=1", "ho st", "://host", "easystock://join?u=bad")
            .forEach { assertEquals(TypedAddress.Invalid, parseTypedAddress(it), it) }
    }

    @Test
    fun aPastedJoinLinkIsACode() {
        val app = assertIs<TypedAddress.Code>(parseTypedAddress(" easystock://join?u=https%3A%2F%2Fh.example&k=invite&t=abc "))
        assertEquals(JoinCode("https://h.example", JoinKind.INVITE, "abc"), app.code)
        val server = assertIs<TypedAddress.Code>(parseTypedAddress("easystock://setup?server=http://10.0.0.2:8080&token=xyz"))
        assertEquals(JoinCode("http://10.0.0.2:8080", JoinKind.SETUP, "xyz"), server.code)
    }
}
