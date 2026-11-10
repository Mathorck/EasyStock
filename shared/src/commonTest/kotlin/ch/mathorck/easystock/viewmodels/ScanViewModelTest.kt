package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.db.ArticleTags
import ch.mathorck.easystock.data.db.TagLinkResult
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.enums.ScanState
import ch.mathorck.easystock.nfc.NfcAvailability
import ch.mathorck.easystock.nfc.TagEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import kotlin.time.Clock
import kotlin.time.Instant

/** In-memory [ArticleTags] following the [ch.mathorck.easystock.data.db.ArticleDao] rules for active rows. */
class FakeArticleTags(vararg articles: Article) : ArticleTags {
    val articles = MutableStateFlow(articles.toList())
    var failing = false

    /** When set, lookups wait for it. */
    var lookupGate: CompletableDeferred<Unit>? = null
    val links = mutableListOf<Triple<String, String, String?>>()

    fun get(id: String) = articles.value.single { it.id == id }

    override suspend fun findByTag(tagId: String): Article? {
        lookupGate?.await()
        if (failing) error("db")
        return articles.value.firstOrNull { it.barcode == tagId }
    }

    override fun observeUntagged(): Flow<List<Article>> =
        articles.map { list -> list.filter { it.barcode == null && !it.archived }.sortedBy { it.name.lowercase() } }

    override suspend fun link(articleId: String, tagId: String, confirmedOwnerId: String?, now: Long): TagLinkResult {
        if (failing) error("db")
        links += Triple(articleId, tagId, confirmedOwnerId)
        if (articles.value.none { it.id == articleId }) return TagLinkResult.ArticleGone
        val owner = articles.value.firstOrNull { it.barcode == tagId && it.id != articleId }
        if (owner != null && owner.id != confirmedOwnerId) return TagLinkResult.LinkedElsewhere(owner)
        articles.value = articles.value.map {
            when {
                it.id == articleId -> it.copy(barcode = tagId, updatedAt = now)
                it.id == owner?.id -> it.copy(barcode = null, updatedAt = now)
                else -> it
            }
        }
        return TagLinkResult.Linked
    }

    override suspend fun unlink(articleId: String, now: Long): Boolean {
        if (failing) error("db")
        val article = articles.value.firstOrNull { it.id == articleId && it.barcode != null } ?: return false
        articles.value = articles.value.map { if (it.id == article.id) it.copy(barcode = null, updatedAt = now) else it }
        return true
    }
}

fun tagged(id: String, name: String, barcode: String? = null, sku: String? = null, archived: Boolean = false) =
    Article(id = id, name = name, barcode = barcode, sku = sku, archived = archived, createdAt = 0, updatedAt = 0)

val fixedClock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(9_000)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val availability = MutableStateFlow(NfcAvailability.Enabled)
    private val tags = FakeArticleTags(
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
        assertEquals("anchor", request.owner.id)
        assertFalse(state.acceptsTags)

        vm.confirmMove()
        assertEquals(Triple("bolt", "FF00", "anchor"), tags.links.last())
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
}
