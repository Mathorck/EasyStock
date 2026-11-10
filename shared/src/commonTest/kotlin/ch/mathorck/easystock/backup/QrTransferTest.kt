package ch.mathorck.easystock.backup

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QrTransferTest {
    private val manifest = BackupManifest(appVersion = "1.0.0", createdAt = "2026-11-10T12:00:00Z")

    // ---------- Base45 ----------

    @Test
    fun base45MatchesRfc9285Examples() {
        assertEquals("BB8", Base45.encode("AB".encodeToByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".encodeToByteArray()))
        assertEquals("UJCLQE7W581", Base45.encode("base-45".encodeToByteArray()))
        assertContentEquals("ietf!".encodeToByteArray(), Base45.decode("QED8WEX0"))
    }

    @Test
    fun base45RoundTripsAnyBytes() {
        for (size in 0..40) {
            val bytes = Random(size).nextBytes(size)
            val text = Base45.encode(bytes)
            assertEquals(Base45.encodedLength(size), text.length)
            assertContentEquals(bytes, Base45.decode(text))
        }
    }

    @Test
    fun base45RejectsInvalidText() {
        assertFailsWith<IllegalArgumentException> { Base45.decode("abc") } // lowercase
        assertFailsWith<IllegalArgumentException> { Base45.decode("GGW") } // 65535 < value
        assertFailsWith<IllegalArgumentException> { Base45.decode("A") } // length
    }

    // ---------- Size decision ----------

    @Test
    fun smallPayloadFitsInAQrCode() {
        val decision = QrTransfer.decide(ByteArray(MAX_QR_PAYLOAD_BYTES), hasPhotos = false)

        val fits = assertIs<QrDecision.Fits>(decision)
        assertTrue(fits.text.length <= MAX_QR_TEXT_CHARS)
        assertTrue(fits.text.startsWith(DATA_QR_PREFIX))
    }

    @Test
    fun payloadOverTheLimitIsTooLarge() {
        assertEquals(QrDecision.TooLarge, QrTransfer.decide(ByteArray(MAX_QR_PAYLOAD_BYTES + 1), hasPhotos = false))
    }

    @Test
    fun backupWithPhotosIsNeverAQrCode() {
        assertEquals(QrDecision.TooLarge, QrTransfer.decide(ByteArray(10), hasPhotos = true))
    }

    @Test
    fun limitStaysInsideVersion40AtMediumCorrection() {
        // Alphanumeric capacity of a version 40-M QR code
        assertTrue(MAX_QR_TEXT_CHARS <= 3391)
        assertTrue(DATA_QR_PREFIX.length + Base45.encodedLength(MAX_QR_PAYLOAD_BYTES) <= MAX_QR_TEXT_CHARS)
    }

    @Test
    fun qrTextUsesOnlyTheAlphanumericAlphabet() {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
        val text = QrTransfer.dataQrText(Random(1).nextBytes(200))
        assertTrue(text.all { it in alphabet })
        val ticket = QrTransfer.wifiQrText(WifiTicket.create("192.168.1.20", 4000, ByteArray(16), ByteArray(32)))
        assertTrue(ticket.all { it in alphabet })
    }

    // ---------- Payload ----------

    @Test
    fun smallStockFitsAndRoundTrips() {
        val bundle = BackupBundle(manifest, BackupFixtures.full())
        val payload = QrTransfer.buildPayload(bundle, secret = null)
        val text = assertIs<QrDecision.Fits>(QrTransfer.decide(payload, hasPhotos = false)).text

        val scanned = assertIs<ScannedQr.Data>(QrTransfer.parse(text))
        assertFalse(BackupCipher.isEncrypted(scanned.payload))
        assertEquals(bundle, QrTransfer.readBundle(scanned.payload))
    }

    @Test
    fun encryptedPayloadRoundTrips() {
        val bundle = BackupBundle(manifest, BackupFixtures.full())
        val secret = BackupSecret.Password("pw".toCharArray(), iterations = 1_000)
        val payload = QrTransfer.buildPayload(bundle, secret)

        assertTrue(BackupCipher.isEncrypted(payload))
        val plain = BackupCipher.decrypt(payload, BackupSecret.Password("pw".toCharArray(), iterations = 1_000))
        assertEquals(bundle, QrTransfer.readBundle(plain))
    }

    @Test
    fun corruptedPayloadIsRejected() {
        val payload = QrTransfer.buildPayload(BackupBundle(manifest, BackupFixtures.full()), secret = null)
        val corrupted = payload.copyOf(payload.size / 2)

        assertFailsWith<BackupException> { QrTransfer.readBundle(corrupted) }
    }

    @Test
    fun otherQrCodeIsNotABackup() {
        assertEquals(BackupError.NOT_A_BACKUP, assertFailsWith<BackupException> { QrTransfer.parse("https://example.com") }.error)
        assertEquals(BackupError.NOT_A_BACKUP, assertFailsWith<BackupException> { QrTransfer.parse("ES1:abc") }.error)
    }

    // ---------- Wi-Fi ticket ----------

    @Test
    fun wifiTicketRoundTrips() {
        val ticket = WifiTicket.create("192.168.1.20", 41234, Random(1).nextBytes(16), Random(2).nextBytes(32))

        assertEquals(ScannedQr.Wifi(ticket), QrTransfer.parse(QrTransfer.wifiQrText(ticket)))
    }

    @Test
    fun wifiTicketWithShortKeyIsRejected() {
        val ticket = WifiTicket.create("192.168.1.20", 41234, ByteArray(16), ByteArray(8))

        assertFailsWith<BackupException> { QrTransfer.parse(QrTransfer.wifiQrText(ticket)) }
    }

    @Test
    fun ticketNeverPrintsItsKey() {
        val ticket = WifiTicket.create("10.0.0.2", 1, ByteArray(16), ByteArray(32))
        assertFalse(ticket.key in ticket.toString())
    }

    @Test
    fun onlyLocalNetworkAddressesAreAccepted() {
        listOf("192.168.1.20", "10.0.2.16", "172.16.0.1", "172.31.255.255", "169.254.1.1", "127.0.0.1", "fe80::1%wlan0", "fd00::1")
            .forEach { assertTrue(isLocalNetworkAddress(it), it) }
        listOf("8.8.8.8", "172.32.0.1", "example.com", "192.168.1", "2001:db8::1", "192.168.01.1", "")
            .forEach { assertFalse(isLocalNetworkAddress(it), it) }
    }
}
