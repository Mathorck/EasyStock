package ch.mathorck.easystock.backup

import ch.mathorck.easystock.composables.settings.formatBytes
import ch.mathorck.easystock.composables.settings.formatCreatedAt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** ZXing encode, then decode from a rendered luminance plane, as the camera would see it. */
class QrCodecTest {
    private fun render(matrix: QrMatrix, scale: Int = 4, quiet: Int = 4): Triple<ByteArray, Int, Int> {
        val side = (matrix.size + 2 * quiet) * scale
        val pixels = ByteArray(side * side) { 0xFF.toByte() }
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!matrix[x, y]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0
            }
        }
        return Triple(pixels, side, side)
    }

    private fun roundTrip(text: String): String? {
        val matrix = assertNotNull(encodeQr(text))
        val (pixels, w, h) = render(matrix)
        return decodeQr(pixels, w, w, h)
    }

    @Test
    fun largestDataQrRoundTrips() {
        val payload = Random(1).nextBytes(MAX_QR_PAYLOAD_BYTES)
        val text = QrTransfer.dataQrText(payload)
        assert(text.length <= MAX_QR_TEXT_CHARS)
        val read = assertNotNull(roundTrip(text))
        assertEquals(text, read)
        val parsed = QrTransfer.parse(read) as ScannedQr.Data
        assert(parsed.payload.contentEquals(payload))
    }

    @Test
    fun wifiQrRoundTrips() {
        val ticket = WifiTicket.create("192.168.1.20", 41234, Random(2).nextBytes(WifiTicket.TOKEN_BYTES), Random(3).nextBytes(32))
        val text = QrTransfer.wifiQrText(ticket)
        assertEquals(text, roundTrip(text))
    }

    @Test
    fun tooLongTextIsNotEncoded() {
        assertNull(encodeQr("A".repeat(5000)))
    }

    @Test
    fun blankImageHasNoCode() {
        assertNull(decodeQr(ByteArray(200 * 200) { 0xFF.toByte() }, 200, 200, 200))
    }

    @Test
    fun formatsSizesAndDates() {
        assertEquals("12 B", formatBytes(12))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("1.5 MB", formatBytes(1536 * 1024))
        assertEquals("2026-11-10 14:03", formatCreatedAt("2026-11-10T14:03:00Z"))
        assertEquals("garbage", formatCreatedAt("garbage"))
    }
}
