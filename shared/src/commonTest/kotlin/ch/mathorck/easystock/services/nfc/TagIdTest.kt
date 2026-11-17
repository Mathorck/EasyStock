package ch.mathorck.easystock.services.nfc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagIdTest {
    @Test
    fun uidIsUppercaseHexWithoutSeparators() {
        assertEquals("04A23B127C8000", tagIdOf(byteArrayOf(0x04, 0xA2.toByte(), 0x3B, 0x12, 0x7C, 0x80.toByte(), 0x00)))
        assertEquals("FF", tagIdOf(byteArrayOf(-1)))
    }

    @Test
    fun emptyOrTooLongUidIsNotAnId() {
        assertNull(tagIdOf(byteArrayOf()))
        assertNull(tagIdOf(ByteArray(MAX_TAG_ID_BYTES + 1)))
        assertEquals(MAX_TAG_ID_BYTES * 2, tagIdOf(ByteArray(MAX_TAG_ID_BYTES))?.length)
    }

    @Test
    fun typedIdIsNormalized() {
        assertEquals("04A23B", normalizeTagId(" 04:a2:3b "))
        assertEquals("04A23B", normalizeTagId("04-A2 3B"))
        assertNull(normalizeTagId(""))
        assertNull(normalizeTagId("04A"))
        assertNull(normalizeTagId("XYZ1"))
    }

    @Test
    fun idIsShownAsColonSeparatedBytes() {
        assertEquals("DE:AD:BE:EF:01", formatTagId("DEADBEEF01"))
        assertEquals("FF", formatTagId("FF"))
        assertEquals("", formatTagId(""))
    }

    @Test
    fun searchKeyDropsSeparatorsAndIgnoresCase() {
        assertEquals("DEADBE", tagIdSearchKey("de:ad-be"))
        assertEquals("BEEF", tagIdSearchKey("be ef"))
        assertEquals("", tagIdSearchKey(" : "))
    }
}
