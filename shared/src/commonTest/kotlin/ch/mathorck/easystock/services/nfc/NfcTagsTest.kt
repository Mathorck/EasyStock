package ch.mathorck.easystock.services.nfc

import ch.mathorck.easystock.enums.nfc.NfcAvailability
import ch.mathorck.easystock.enums.nfc.TagPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

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

    @Test
    fun aTagReadWithoutListenerIsNotRemembered() {
        assertFalse(tags.dispatch(TagEvent.Read("AA")))
        val read = mutableListOf<TagEvent>()
        tags.register(TagPriority.entries.first()) { read += it }
        // The screen that opens right after still gets the tag held against the phone
        assertTrue(tags.dispatch(TagEvent.Read("AA")))
        assertEquals(listOf<TagEvent>(TagEvent.Read("AA")), read)
    }
}
