package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.db.TagOwner
import ch.mathorck.easystock.data.db.TagTarget
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagRowsTest {
    private val garage = place("g", "Garage")
    private val shelf = place("s", "Shelf", parentId = "g", barcode = "CAFE0001")
    private val bin = place("b", "Bin", barcode = "0A0B")

    @Test
    fun onlyActiveTaggedRowsAreListedByName() {
        val rows = buildTagRows(
            articles = listOf(
                tagged("a1", "drill", barcode = "DEADBEEF01"),
                tagged("a2", "Untagged"),
                tagged("a3", "Gone", barcode = "11").copy(deletedAt = 5),
            ),
            locations = listOf(garage, shelf, bin, place("x", "Old", barcode = "22").copy(deletedAt = 5)),
        )
        assertEquals(listOf("Bin", "drill", "Shelf"), rows.map { it.name })
        assertEquals(listOf("0A0B", "DEADBEEF01", "CAFE0001"), rows.map { it.tagId })
        assertEquals(TagTarget.OfArticle("a1"), rows[1].target)
        assertEquals(TagTarget.OfLocation("s"), rows[2].target)
    }

    @Test
    fun locationShowsItsPathOnlyWhenNested() {
        val rows = buildTagRows(emptyList(), listOf(garage, shelf, bin))
        assertEquals("Garage › Shelf", rows.single { it.name == "Shelf" }.subtitle)
        assertNull(rows.single { it.name == "Bin" }.subtitle)
        assertTrue(rows.all { it.isLocation })
    }

    @Test
    fun articleShowsSkuPropertiesAndArchived() {
        val rows = buildTagRows(
            listOf(tagged("a", "Drill", barcode = "01", sku = "D-1", archived = true), tagged("b", "Saw", barcode = "02")),
            emptyList(),
        ) { if (it.id == "a") "18 V" else null }
        assertEquals("D-1 · 18 V", rows[0].subtitle)
        assertTrue(rows[0].archived)
        assertNull(rows[1].subtitle)
        assertFalse(rows[1].archived)
    }

    private val sample = listOf(
        TagRow("DEADBEEF01", TagTarget.OfArticle("a"), "Drill"),
        TagRow("CAFE0001", TagTarget.OfLocation("l"), "Caisse cordes"),
        TagRow("0A0B", TagTarget.OfArticle("b"), "adapter"),
    )

    @Test
    fun searchMatchesNameOrTagId() {
        assertEquals(listOf("Drill"), sample.searchTags("dri").map { it.name })
        assertEquals(listOf("Drill"), sample.searchTags("be:ef").map { it.name })
        assertEquals(listOf("Caisse cordes"), sample.searchTags(" cafe ").map { it.name })
        assertEquals(sample, sample.searchTags("  "))
        assertEquals(emptyList(), sample.searchTags("zzz"))
    }

    @Test
    fun filterKeepsOneType() {
        assertEquals(listOf("Drill", "adapter"), sample.filterTags(TagListFilter.ARTICLES).map { it.name })
        assertEquals(listOf("Caisse cordes"), sample.filterTags(TagListFilter.LOCATIONS).map { it.name })
        assertEquals(sample, sample.filterTags(TagListFilter.ALL))
    }

    @Test
    fun sortByNameIgnoresCaseOrByTagId() {
        assertEquals(listOf("adapter", "Caisse cordes", "Drill"), sample.sortTags(TagListSort.NAME).map { it.name })
        assertEquals(listOf("0A0B", "CAFE0001", "DEADBEEF01"), sample.sortTags(TagListSort.TAG_ID).map { it.tagId })
        assertEquals(listOf("0A0B", "DEADBEEF01"), sample.displayedTags("", TagListFilter.ARTICLES, TagListSort.TAG_ID).map { it.tagId })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TagListViewModelTest {
    private lateinit var store: FakeTagStore
    private lateinit var vm: TagListViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = FakeTagStore(
            tagged("a1", "Drill", barcode = "DEADBEEF01"),
            tagged("a2", "Saw"),
        )
        store.locations.value = listOf(place("l1", "Caisse cordes", barcode = "CAFE0001"), place("l2", "Shelf"))
        vm = TagListViewModel(store, clock = fixedClock)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.state.value

    @Test
    fun listsEveryTagWithCounts() {
        assertFalse(state.loading)
        assertEquals(listOf("Caisse cordes", "Drill"), state.rows.map { it.name })
        assertEquals(1, state.articleCount)
        assertEquals(1, state.locationCount)
        assertTrue(state.acceptsTags)
    }

    @Test
    fun searchAndFilterNarrowTheRowsButNotTheCounts() {
        vm.onQueryChange("drill")
        assertEquals(listOf("Drill"), state.rows.map { it.name })
        vm.onQueryChange("")
        vm.onFilterChange(TagListFilter.LOCATIONS)
        assertEquals(listOf("Caisse cordes"), state.rows.map { it.name })
        assertEquals(2, state.all.size)
    }

    @Test
    fun scanningAKnownTagHighlightsItAndClearsWhatHidesIt() {
        vm.onFilterChange(TagListFilter.LOCATIONS)
        vm.onQueryChange("caisse")
        vm.onTag(TagEvent.Read("DEADBEEF01"))
        assertEquals("DEADBEEF01", state.highlight?.tagId)
        assertEquals("", state.query)
        assertEquals(TagListFilter.ALL, state.filter)
        assertTrue(state.rows.any { it.tagId == "DEADBEEF01" })
    }

    @Test
    fun highlightKeepsAFilterThatShowsTheRow() {
        vm.onFilterChange(TagListFilter.ARTICLES)
        vm.onQueryChange("caisse")
        vm.highlightTag("DEADBEEF01")
        assertEquals("", state.query)
        assertEquals(TagListFilter.ARTICLES, state.filter)
    }

    @Test
    fun aNewerHighlightIsNotCleared() {
        vm.highlightTag("DEADBEEF01")
        val first = assertNotNull(state.highlight).seq
        vm.highlightTag("DEADBEEF01")
        vm.clearHighlight(first)
        assertNotNull(state.highlight)
        vm.clearHighlight(state.highlight!!.seq)
        assertNull(state.highlight)
    }

    @Test
    fun scanningAnUnknownTagOffersToLinkIt() {
        vm.onTag(TagEvent.Read("0A0B0C0D"))
        assertEquals("0A0B0C0D", state.unknownTagId)
        assertNull(state.highlight)

        vm.linkUnknown(TagPickerType.LOCATION)
        assertNull(state.unknownTagId)
        assertEquals("0A0B0C0D", state.pickerTagId)
        assertEquals(listOf("Shelf"), state.picker?.locations?.map { it.name })
        assertFalse(state.acceptsTags)

        vm.pick("l2")
        assertNull(state.picker)
        assertEquals("0A0B0C0D", store.location("l2").barcode)
        assertEquals("0A0B0C0D", state.highlight?.tagId)
        assertEquals(3, state.all.size)
    }

    @Test
    fun scanWhileAPickerIsOpenIsIgnored() {
        vm.startReassign(state.rows.single { it.name == "Drill" }, TagPickerType.ARTICLE)
        vm.onTag(TagEvent.Read("0A0B0C0D"))
        assertNull(state.unknownTagId)
    }

    @Test
    fun failedReadOrLookupShowsAMessage() {
        vm.onTag(TagEvent.Failed)
        assertEquals(TagListMessage.READ_FAILED, state.message)
        vm.consumeMessage()
        store.failing = true
        vm.onTag(TagEvent.Read("0A0B0C0D"))
        assertEquals(TagListMessage.LOOKUP_FAILED, state.message)
        assertNull(state.unknownTagId)
    }

    @Test
    fun unlinkAsksThenClearsTheTagWithoutDeleting() {
        val row = state.rows.single { it.name == "Drill" }
        vm.requestUnlink(row)
        assertEquals(row, state.pendingUnlink)
        assertFalse(state.acceptsTags)
        vm.cancelUnlink()
        assertEquals("DEADBEEF01", store.get("a1").barcode)

        vm.requestUnlink(row)
        vm.confirmUnlink()
        assertNull(state.pendingUnlink)
        assertNull(store.get("a1").barcode)
        assertEquals(9_000L, store.get("a1").updatedAt)
        assertEquals("Drill", store.get("a1").name)
        assertEquals(TagListMessage.UNLINKED, state.message)
        assertEquals(listOf("Caisse cordes"), state.rows.map { it.name })
    }

    @Test
    fun failedUnlinkShowsAMessage() {
        store.failing = true
        vm.requestUnlink(state.rows.first())
        vm.confirmUnlink()
        assertEquals(TagListMessage.UNLINK_FAILED, state.message)
    }

    @Test
    fun reassignAsksBeforeMovingTheTagToAnotherType() {
        val row = state.rows.single { it.name == "Drill" }
        vm.startReassign(row, TagPickerType.LOCATION)
        assertEquals("DEADBEEF01", state.pickerTagId)
        vm.pick("l2")

        val request = assertNotNull(state.moveRequest)
        assertIs<TagOwner.OfArticle>(request.owner)
        assertEquals("Shelf", request.targetName)
        assertEquals("DEADBEEF01", store.get("a1").barcode)

        vm.confirmMove()
        assertNull(state.moveRequest)
        assertNull(state.picker)
        assertNull(store.get("a1").barcode)
        assertEquals("DEADBEEF01", store.location("l2").barcode)
        assertEquals("DEADBEEF01", state.highlight?.tagId)
        assertTrue(state.rows.single { it.tagId == "DEADBEEF01" }.isLocation)
    }

    @Test
    fun cancelledMoveKeepsTheTagAndThePicker() {
        vm.startReassign(state.rows.single { it.name == "Drill" }, TagPickerType.ARTICLE)
        assertEquals(listOf("Saw"), state.picker?.articles?.map { it.name })
        vm.pick("a2")
        assertNotNull(state.moveRequest)
        vm.cancelMove()
        assertNull(state.moveRequest)
        assertNotNull(state.picker)
        assertEquals("DEADBEEF01", store.get("a1").barcode)
        vm.closePicker()
        assertTrue(state.acceptsTags)
    }
}
