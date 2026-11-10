package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.nfc.TagEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TagLinkViewModelTest {
    private val tags = FakeArticleTags(
        tagged("drill", "Drill", barcode = "AA01"),
        tagged("bolt", "Bolt"),
    )
    private lateinit var vm: TagLinkViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = TagLinkViewModel("bolt", tags, fixedClock)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.state.value

    @Test
    fun tagsAreTakenOnlyWhileTheSheetWaits() {
        assertFalse(state.acceptsTags)
        vm.onTag(TagEvent.Read("FF00"))
        assertNull(tags.get("bolt").barcode)

        vm.start()
        assertTrue(state.acceptsTags)
        vm.cancel()
        assertFalse(state.waiting)
    }

    @Test
    fun freeTagIsLinkedAndTheSheetCloses() {
        vm.start()
        vm.onTag(TagEvent.Read("FF00"))

        assertEquals("FF00", tags.get("bolt").barcode)
        assertEquals(9_000, tags.get("bolt").updatedAt)
        assertFalse(state.waiting)
    }

    @Test
    fun failedReadKeepsWaiting() {
        vm.start()
        vm.onTag(TagEvent.Failed)

        assertTrue(state.readFailed)
        assertTrue(state.acceptsTags)

        vm.onTag(TagEvent.Read("FF00"))
        assertEquals("FF00", tags.get("bolt").barcode)
    }

    @Test
    fun tagOfAnotherArticleIsMovedAfterConfirmation() {
        vm.start()
        vm.onTag(TagEvent.Read("AA01"))

        val request = assertNotNull(state.moveRequest)
        assertEquals("drill", request.owner.id)
        assertFalse(state.acceptsTags)
        assertEquals("AA01", tags.get("drill").barcode)

        vm.confirmMove()
        assertEquals("AA01", tags.get("bolt").barcode)
        assertNull(tags.get("drill").barcode)
        assertEquals(9_000, tags.get("drill").updatedAt)
        assertFalse(state.waiting)
    }

    @Test
    fun cancelledMoveKeepsWaitingForAnotherTag() {
        vm.start()
        vm.onTag(TagEvent.Read("AA01"))
        vm.cancelMove()

        assertNull(state.moveRequest)
        assertTrue(state.acceptsTags)
        assertEquals("AA01", tags.get("drill").barcode)
    }

    @Test
    fun saveFailureIsShownAndTheSheetStays() {
        vm.start()
        tags.failing = true
        vm.onTag(TagEvent.Read("FF00"))

        assertTrue(state.saveFailed)
        assertTrue(state.acceptsTags)
    }

    @Test
    fun removeClearsTheTag() {
        val drill = TagLinkViewModel("drill", tags, fixedClock)
        drill.removeTag()

        assertNull(tags.get("drill").barcode)
        assertFalse(drill.state.value.removeFailed)
    }

    @Test
    fun removeFailureIsShown() {
        tags.failing = true
        vm.removeTag()

        assertTrue(state.removeFailed)
    }
}
