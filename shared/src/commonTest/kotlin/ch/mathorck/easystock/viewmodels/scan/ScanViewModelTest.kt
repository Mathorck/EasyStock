package ch.mathorck.easystock.viewmodels.scan

import ch.mathorck.easystock.data.dao.tags.FakeTagStore
import ch.mathorck.easystock.data.dao.tags.TagOwner
import ch.mathorck.easystock.data.dao.tags.TagTarget
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.ScanState
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import ch.mathorck.easystock.enums.nfc.NfcAvailability
import ch.mathorck.easystock.enums.tags.TagPickerType
import ch.mathorck.easystock.services.attributes.AttributeCatalog
import ch.mathorck.easystock.services.nfc.TagEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonUnquotedLiteral

fun place(id: String, name: String = id, parentId: String? = null, barcode: String? = null) =
    Location(id = id, name = name, parentId = parentId, barcode = barcode, createdAt = 0, updatedAt = 0)

fun tagged(id: String, name: String, barcode: String? = null, sku: String? = null, archived: Boolean = false) =
    Article(id = id, name = name, barcode = barcode, sku = sku, archived = archived, createdAt = 0, updatedAt = 0)

val fixedClock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(9_000)
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class ScanViewModelTest {
    private val availability = MutableStateFlow(NfcAvailability.Enabled)
    private val tags = FakeTagStore(
        tagged("drill", "Drill", barcode = "AA01"),
        tagged("bolt", "Bolt M8", sku = "B-8"),
        tagged("anchor", "Anchor"),
        tagged("old", "Old saw", archived = true),
    )
    private lateinit var vm: ScanViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ScanViewModel(tags, availability, fixedClock)
    }

    @Test
    fun pickerShowsAndSearchesThePropertiesOfSameNameArticles() {
        val length = AttributeDefinition(id = "len", key = "length", label = "Length", dataType = AttributeDataType.NUMBER, unit = "m", createdAt = 0, updatedAt = 0)
        fun rope(id: String, metres: String?) = tagged(id, "Rope").copy(
            attributes = metres?.let { JsonObject(mapOf("length" to JsonUnquotedLiteral(it))) },
        )
        val store = FakeTagStore(rope("r50", "50"), rope("r30", "30"), rope("plain", null))
        val withProps = ScanViewModel(store, availability, fixedClock, flowOf(AttributeCatalog(listOf(length), emptyList())))
        withProps.onTag(TagEvent.Read("ZZ99"))
        withProps.openPicker()
        val picker = assertNotNull(withProps.state.value.picker)
        assertEquals(mapOf("r50" to "50 m", "r30" to "30 m"), picker.attributeLines, "no line for an article without properties")
        withProps.onPickerQuery("50")
        assertEquals(listOf("r50"), withProps.state.value.picker?.articles?.map { it.id })
        withProps.onPickerQuery("3000 cm")
        assertEquals(listOf("r30"), withProps.state.value.picker?.articles?.map { it.id })
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.state.value

    @Test
    fun circleFollowsTheNfcAvailabilityWhileWaiting() {
        assertEquals(ScanState.Ready, state.scanState)
        availability.value = NfcAvailability.Disabled
        assertEquals(ScanState.NfcDisabled, state.scanState)
        availability.value = NfcAvailability.Unavailable
        assertEquals(ScanState.NfcUnavailable, state.scanState)
        availability.value = NfcAvailability.Unknown
        assertEquals(ScanState.Ready, state.scanState)
    }

    @Test
    fun knownTagOpensItsArticle() {
        vm.onTag(TagEvent.Read("AA01"))

        assertEquals("drill", state.openArticleId)
        assertEquals(ScanState.Success, state.scanState)
        assertFalse(state.acceptsTags)

        vm.reset()
        assertEquals(ScanPhase.Ready, state.phase)
        assertTrue(state.acceptsTags)
    }

    @Test
    fun unknownTagIsNotFound() {
        vm.onTag(TagEvent.Read("FF00"))

        assertEquals(ScanState.NotFound, state.scanState)
        assertEquals("FF00", state.unknownTagId)
        // Another tag can be scanned from there
        assertTrue(state.acceptsTags)
        vm.onTag(TagEvent.Read("AA01"))
        assertEquals("drill", state.openArticleId)
    }

    @Test
    fun failedReadOrLookupIsAnErrorUntilReset() {
        vm.onTag(TagEvent.Failed)
        assertEquals(ScanState.Error, state.scanState)
        vm.reset()
        assertEquals(ScanState.Ready, state.scanState)

        tags.failing = true
        vm.onTag(TagEvent.Read("AA01"))
        assertEquals(ScanState.Error, state.scanState)
    }

    @Test
    fun phaseWinsOverAvailability() {
        // e.g. a fake tag on the NFC-less emulator
        availability.value = NfcAvailability.Unavailable
        vm.onTag(TagEvent.Read("FF00"))
        assertEquals(ScanState.NotFound, state.scanState)
    }

    @Test
    fun tagsAreIgnoredWhileReading() {
        val gate = CompletableDeferred<Unit>()
        tags.lookupGate = gate
        vm.onTag(TagEvent.Read("AA01"))
        assertEquals(ScanState.Reading, state.scanState)
        assertFalse(state.acceptsTags)

        vm.onTag(TagEvent.Read("FF00"))
        gate.complete(Unit)

        assertEquals("drill", state.openArticleId)
    }

    @Test
    fun lookupFinishingAfterAResetIsDropped() {
        val gate = CompletableDeferred<Unit>()
        tags.lookupGate = gate
        vm.onTag(TagEvent.Read("AA01"))
        vm.reset()
        gate.complete(Unit)

        assertEquals(ScanPhase.Ready, state.phase)
    }

    @Test
    fun pickerListsUntaggedActiveArticlesAndFilters() {
        vm.openPicker()
        assertNull(state.picker, "only for an unknown tag")

        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker()
        val picker = assertNotNull(state.picker)
        assertEquals(listOf("anchor", "bolt"), picker.articles?.map { it.id })
        assertFalse(picker.noneUntagged)
        assertFalse(state.acceptsTags)

        vm.onPickerQuery("b-8")
        assertEquals(listOf("bolt"), state.picker?.articles?.map { it.id })
        vm.onPickerQuery("zzz")
        assertEquals(emptyList(), state.picker?.articles)
        assertFalse(state.picker!!.noneUntagged)

        vm.closePicker()
        assertNull(state.picker)
        assertEquals("FF00", state.unknownTagId)
    }

    @Test
    fun pickerSaysWhenNoArticleIsUntagged() {
        tags.articles.value = listOf(tagged("drill", "Drill", barcode = "AA01"))
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker()

        assertTrue(state.picker!!.noneUntagged)
    }

    @Test
    fun linkingFromThePickerOpensTheArticle() {
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker()
        vm.linkTo("bolt")

        assertEquals("FF00", tags.get("bolt").barcode)
        assertEquals(9_000, tags.get("bolt").updatedAt)
        assertEquals("bolt", state.openArticleId)
        assertNull(state.picker)
    }

    @Test
    fun linkFailureIsShownInThePicker() {
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker()
        tags.failing = true
        vm.linkTo("bolt")

        assertTrue(state.picker!!.saveFailed)
        assertFalse(state.picker!!.saving)
        assertEquals("FF00", state.unknownTagId)
    }

    @Test
    fun tagTakenMeanwhileAsksBeforeMoving() {
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker()
        // Another screen linked the tag in the meantime
        tags.articles.value = tags.articles.value.map { if (it.id == "anchor") it.copy(barcode = "FF00") else it }
        vm.linkTo("bolt")

        val request = assertNotNull(state.moveRequest)
        assertEquals(TagTarget.OfArticle("anchor"), request.owner.target)
        assertEquals("Bolt M8", request.targetName)
        assertFalse(state.acceptsTags)

        vm.confirmMove()
        assertEquals(Triple(TagTarget.OfArticle("bolt"), "FF00", TagTarget.OfArticle("anchor")), tags.links.last())
        assertEquals("FF00", tags.get("bolt").barcode)
        assertNull(tags.get("anchor").barcode)
        assertEquals("bolt", state.openArticleId)
    }

    @Test
    fun cancelledMoveKeepsThePicker() {
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker()
        tags.articles.value = tags.articles.value.map { if (it.id == "anchor") it.copy(barcode = "FF00") else it }
        vm.linkTo("bolt")
        vm.cancelMove()

        assertNull(state.moveRequest)
        assertNotNull(state.picker)
        assertEquals("anchor", tags.articles.value.single { it.barcode == "FF00" }.id)
    }

    @Test
    fun createdArticleIsOpened() {
        vm.onTag(TagEvent.Read("FF00"))
        vm.openArticle("new")

        assertEquals("new", state.openArticleId)
    }

    @Test
    fun locationTagShowsItsContents() {
        tags.locations.value = listOf(place("crate", "Crate", barcode = "CC01"))
        vm.onTag(TagEvent.Read("CC01"))

        assertEquals("crate", state.openLocationId)
        assertNull(state.openArticleId)
        assertEquals(ScanState.Success, state.scanState)
        assertFalse(state.acceptsTags)

        vm.reset()
        assertNull(state.openLocationId)
        assertTrue(state.acceptsTags)
    }

    @Test
    fun locationPickerListsUntaggedLocationsWithPaths() {
        tags.locations.value = listOf(
            place("ws", "Workshop"),
            place("rack", "Rack", parentId = "ws"),
            place("crate", "Crate", barcode = "CC01"),
        )
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker(TagPickerType.LOCATION)

        val picker = assertNotNull(state.picker)
        assertEquals(TagPickerType.LOCATION, picker.type)
        // Tree order, the tagged crate left out
        assertEquals(listOf("ws", "rack"), picker.locations?.map { it.id })
        assertEquals("Workshop › Rack", picker.locations?.last()?.path)

        vm.onPickerQuery("work")
        // The path matches: the rack is under the workshop
        assertEquals(setOf("rack", "ws"), state.picker?.locations?.map { it.id }?.toSet())
        vm.onPickerQuery("rack")
        assertEquals(listOf("rack"), state.picker?.locations?.map { it.id })
    }

    @Test
    fun locationPickerSaysWhenAllAreTagged() {
        tags.locations.value = listOf(place("crate", "Crate", barcode = "CC01"))
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker(TagPickerType.LOCATION)

        assertTrue(state.picker!!.noneUntagged)
    }

    @Test
    fun linkingALocationShowsItsContents() {
        tags.locations.value = listOf(place("crate", "Crate"))
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker(TagPickerType.LOCATION)
        vm.linkTo("crate")

        assertEquals("FF00", tags.location("crate").barcode)
        assertEquals("crate", state.openLocationId)
    }

    @Test
    fun articleTagTakenMeanwhileAsksBeforeMovingToALocation() {
        tags.locations.value = listOf(place("crate", "Crate"))
        vm.onTag(TagEvent.Read("FF00"))
        vm.openPicker(TagPickerType.LOCATION)
        tags.articles.value = tags.articles.value.map { if (it.id == "anchor") it.copy(barcode = "FF00") else it }
        vm.linkTo("crate")

        val request = assertNotNull(state.moveRequest)
        assertTrue(request.owner is TagOwner.OfArticle)
        assertEquals(TagTarget.OfLocation("crate"), request.target)
        assertEquals("Crate", request.targetName)

        vm.confirmMove()
        assertEquals("FF00", tags.location("crate").barcode)
        assertNull(tags.get("anchor").barcode)
        assertEquals("crate", state.openLocationId)
    }

    @Test
    fun createdLocationIsShown() {
        vm.onTag(TagEvent.Read("FF00"))
        vm.openLocation("new")

        assertEquals("new", state.openLocationId)
    }
}
