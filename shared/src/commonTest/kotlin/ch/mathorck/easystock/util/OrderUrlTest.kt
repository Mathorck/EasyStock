package ch.mathorck.easystock.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderUrlTest {
    @Test
    fun blankIsNoLinkAndValid() {
        assertEquals(OrderUrlCheck.Empty, checkOrderUrl(""))
        assertEquals(OrderUrlCheck.Empty, checkOrderUrl("  \t "))
        assertNull(orderUrlOrNull("   "))
    }

    @Test
    fun httpAndHttpsAreAcceptedAndTrimmed() {
        assertEquals(OrderUrlCheck.Valid("https://shop.example/p?id=1&b=2#x"), checkOrderUrl("  https://shop.example/p?id=1&b=2#x \n"))
        assertEquals(OrderUrlCheck.Valid("http://shop.example"), checkOrderUrl("http://shop.example"))
        assertEquals(OrderUrlCheck.Valid("HTTPS://Shop.Example/a"), checkOrderUrl("HTTPS://Shop.Example/a"))
        assertEquals(OrderUrlCheck.Valid("http://192.168.1.5:8080/x"), checkOrderUrl("http://192.168.1.5:8080/x"))
        assertEquals("https://a.ch", orderUrlOrNull(" https://a.ch "))
    }

    @Test
    fun otherSchemesAreRejected() {
        listOf(
            "javascript:alert(1)", "intent://scan/#Intent;scheme=zxing;end", "file:///etc/passwd", "content://x/y",
            "ftp://shop.example", "tel:+41000", "mailto:a@b.ch", "data:text/html,hi", "market://details?id=x",
            "//shop.example/x", "shop.example/x", "www.shop.example",
        ).forEach { assertEquals(OrderUrlCheck.Invalid, checkOrderUrl(it), it) }
    }

    @Test
    fun hostIsRequired() {
        listOf(
            "http://", "https://", "https:///path", "https://?q=1", "https://#f", "https://:443/x",
            "https:/shop.example", "https:shop.example", "https://.example", "https://-a.example",
        ).forEach { assertEquals(OrderUrlCheck.Invalid, checkOrderUrl(it), it) }
    }

    @Test
    fun malformedUrlsAreRejected() {
        listOf(
            "https://shop example/x", "https://shop.example/a b", "https://shop.example/\u0000", "https://shop.example/\n x",
            "https://user@shop.example", "https://shop.example@evil.example/x", "https://shop.example:abc/x",
            "https://shop.example:123456/x", "https://shop.example:/x", "https://sh<op>.example", "https://[::1]/x",
        ).forEach { assertEquals(OrderUrlCheck.Invalid, checkOrderUrl(it), it) }
    }

    @Test
    fun lengthIsLimitedTo2048() {
        val prefix = "https://a.ch/"
        val max = prefix + "x".repeat(MAX_ORDER_URL_LENGTH - prefix.length)
        assertEquals(OrderUrlCheck.Valid(max), checkOrderUrl(max))
        assertEquals(OrderUrlCheck.TooLong, checkOrderUrl(max + "x"))
        // Spaces around the value do not count
        assertEquals(OrderUrlCheck.Valid(max), checkOrderUrl("  $max  "))
        // Too long wins over any other problem
        assertEquals(OrderUrlCheck.TooLong, checkOrderUrl("javascript:" + "x".repeat(MAX_ORDER_URL_LENGTH)))
    }

    @Test
    fun hostIsShownWithoutPortOrPath() {
        assertEquals("shop.example", orderUrlHost("https://shop.example/p?q=1"))
        assertEquals("shop.example", orderUrlHost("http://shop.example:8080"))
        assertEquals("shop.example", orderUrlHost("https://shop.example?x=1"))
        assertNull(orderUrlHost("javascript:alert(1)"))
        assertNull(orderUrlHost("not a url"))
    }

    @Test
    fun openableOnlyForValidWebUrls() {
        assertTrue(isOpenableOrderUrl("https://shop.example/x"))
        assertTrue(isOpenableOrderUrl("http://shop.example/x"))
        assertFalse(isOpenableOrderUrl("javascript:alert(1)"))
        assertFalse(isOpenableOrderUrl("intent://x#Intent;end"))
        assertFalse(isOpenableOrderUrl(""))
        assertFalse(isOpenableOrderUrl(" https://shop.example"))
    }
}
