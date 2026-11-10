package ch.mathorck.easystock.nfc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

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
}

class NfcTagsTest {
    private val time = TestTimeSource()
    private val tags = NfcTags(timeSource = time, repeatDelay = 1_500.milliseconds)

    @Test
    fun noListenerTakesNothing() {
        assertFalse(tags.dispatch(TagEvent.Read("AA")))
        assertFalse(tags.listening.value)
    }

    @Test
    fun linkListenerWinsOverLookup() {
        val got = mutableListOf<String>()
        tags.register(TagPriority.Link) { got += "link" }
        tags.register(TagPriority.Lookup) { got += "lookup" }

        assertTrue(tags.dispatch(TagEvent.Read("AA")))

        assertEquals(listOf("link"), got)
    }

    @Test
    fun mostRecentListenerOfTheSamePriorityWins() {
        val got = mutableListOf<String>()
        tags.register(TagPriority.Lookup) { got += "first" }
        val unregister = tags.register(TagPriority.Lookup) { got += "second" }

        tags.dispatch(TagEvent.Read("AA"))
        unregister()
        tags.dispatch(TagEvent.Read("BB"))

        assertEquals(listOf("second", "first"), got)
    }

    @Test
    fun listeningFollowsTheListeners() {
        val first = tags.register(TagPriority.Lookup) {}
        val second = tags.register(TagPriority.Link) {}
        assertTrue(tags.listening.value)

        first()
        assertTrue(tags.listening.value)
        second()
        assertFalse(tags.listening.value)
    }

    @Test
    fun sameTagAgainWithinTheDelayIsIgnored() {
        val got = mutableListOf<TagEvent>()
        tags.register(TagPriority.Lookup) { got += it }

        assertTrue(tags.dispatch(TagEvent.Read("AA")))
        time += 1_000.milliseconds
        assertFalse(tags.dispatch(TagEvent.Read("AA")))
        // Another tag goes through at once
        assertTrue(tags.dispatch(TagEvent.Read("BB")))
        time += 1_600.milliseconds
        assertTrue(tags.dispatch(TagEvent.Read("BB")))
        // Failed reads are never filtered
        assertTrue(tags.dispatch(TagEvent.Failed))
        assertTrue(tags.dispatch(TagEvent.Failed))

        assertEquals(5, got.size)
    }

    @Test
    fun availabilityIsSetByThePlatform() {
        assertEquals(NfcAvailability.Unknown, tags.availability.value)
        tags.setAvailability(NfcAvailability.Disabled)
        assertEquals(NfcAvailability.Disabled, tags.availability.value)
    }
}
