package ch.mathorck.easystock.viewmodels.alerts

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class DateAlertsTest {
    private val today = LocalDate(2026, 11, 18)

    private fun date(flag: Boolean = true, notify: Boolean = false, key: String = "expiry", deleted: Boolean = false) = AttributeDefinition(
        id = "d-$key", key = key, label = "Expiry $key", dataType = AttributeDataType.DATE,
        flagWhenPast = flag, notifyWhenPast = notify, createdAt = 0, updatedAt = 0, deletedAt = if (deleted) 1 else null,
    )

    private fun values(key: String, value: String) = JsonObject(mapOf(key to JsonPrimitive(value)))

    private fun article(id: String, value: String? = null, key: String = "expiry", deleted: Boolean = false) = Article(
        id = id, name = "Article $id", attributes = value?.let { values(key, it) }, createdAt = 0, updatedAt = 0,
        deletedAt = if (deleted) 1 else null,
    )

    private fun item(
        id: String, articleId: String, value: String? = null, key: String = "expiry", deleted: Boolean = false,
        status: ItemStatus = ItemStatus.AVAILABLE, locationId: String? = "loc",
    ) = Item(
        id = id, articleId = articleId, status = status, locationId = locationId, attributes = value?.let { values(key, it) }, createdAt = 0, updatedAt = 0,
        deletedAt = if (deleted) 1 else null,
    )

    @Test
    fun aDateIsPastFromTheDayAfter() {
        assertFalse(isDatePast(JsonPrimitive("2026-11-18"), today))
        assertTrue(isDatePast(JsonPrimitive("2026-11-17"), today))
        assertFalse(isDatePast(JsonPrimitive("2026-11-19"), today))
    }

    @Test
    fun invalidOrEmptyValuesAreIgnored() {
        assertFalse(isDatePast(null, today))
        assertFalse(isDatePast(JsonPrimitive(""), today))
        assertFalse(isDatePast(JsonPrimitive("yesterday"), today))
        assertFalse(isDatePast(JsonPrimitive("2026-13-40"), today))
        assertFalse(isDatePast(JsonPrimitive(20200101), today))
    }

    @Test
    fun articleOwnDateIsFlagged() {
        val past = pastDates(listOf(date()), listOf(article("a1", "2026-01-01"), article("a2", "2027-01-01")), emptyList(), today)
        assertEquals(listOf("a1"), past.map { it.articleId })
        assertEquals(setOf("a1"), flaggedArticleIds(past))
        assertFalse(past.single().isItem)
        assertEquals("Expiry expiry", past.single().label)
    }

    @Test
    fun itemDateFlagsItsArticle() {
        val past = pastDates(
            listOf(date()),
            listOf(article("a1", "2027-01-01")),
            listOf(item("i1", "a1", "2026-01-01"), item("i2", "a1", "2027-06-01")),
            today,
        )
        assertEquals(listOf("i1"), past.map { it.targetId })
        assertTrue(past.single().isItem)
        assertEquals(setOf("a1"), flaggedArticleIds(past))
    }

    @Test
    fun deletedRowsAndDefinitionsAreIgnored() {
        val articles = listOf(article("a1", "2026-01-01", deleted = true), article("a2", "2027-01-01"), article("a3"))
        val items = listOf(
            item("i1", "a2", "2026-01-01", deleted = true),
            item("i2", "a1", "2026-01-01"), // article deleted
            item("i3", "unknown", "2026-01-01"),
        )
        assertTrue(pastDates(listOf(date()), articles, items, today).isEmpty())
        assertTrue(pastDates(listOf(date(deleted = true)), listOf(article("a1", "2026-01-01")), emptyList(), today).isEmpty())
    }

    @Test
    fun definitionWithFlagOffIsIgnored() {
        assertTrue(pastDates(listOf(date(flag = false)), listOf(article("a1", "2026-01-01")), emptyList(), today).isEmpty())
    }

    @Test
    fun onlyDateDefinitionsCount() {
        val text = date().copy(dataType = AttributeDataType.TEXT)
        assertTrue(pastDates(listOf(text), listOf(article("a1", "2026-01-01")), emptyList(), today).isEmpty())
    }

    @Test
    fun otherKeysAreNotMixedUp() {
        val past = pastDates(listOf(date(key = "expiry")), listOf(article("a1", "2026-01-01", key = "other")), emptyList(), today)
        assertTrue(past.isEmpty())
    }

    @Test
    fun notifyFlagComesFromTheDefinition() {
        val past = pastDates(
            listOf(date(notify = true, key = "k1"), date(notify = false, key = "k2")),
            listOf(Article(id = "a1", name = "A", attributes = JsonObject(mapOf("k1" to JsonPrimitive("2026-01-01"), "k2" to JsonPrimitive("2026-01-01"))), createdAt = 0, updatedAt = 0)),
            emptyList(),
            today,
        )
        assertEquals(mapOf("k1" to true, "k2" to false), past.associate { it.attributeKey to it.notify })
    }

    @Test
    fun noticesSkipDatesAlreadyNotifiedAndNonNotifyDefinitions() {
        val past = pastDates(
            listOf(date(notify = true, key = "k1"), date(notify = false, key = "k2")),
            listOf(
                Article(id = "a1", name = "A", attributes = JsonObject(mapOf("k1" to JsonPrimitive("2026-01-01"), "k2" to JsonPrimitive("2026-01-01"))), createdAt = 0, updatedAt = 0),
                article("a2", "2026-02-02", key = "k1"),
            ),
            emptyList(),
            today,
        )
        val notified = setOf(Triple("a1", "k1", "2026-01-01"))
        assertEquals(listOf("a2"), datesToNotify(past, notified).map { it.targetId })
        assertEquals(listOf("a1", "a2"), datesToNotify(past, emptySet()).map { it.targetId })
    }

    @Test
    fun aChangedDateNotifiesAgain() {
        val past = pastDates(listOf(date(notify = true)), listOf(article("a1", "2026-03-03")), emptyList(), today)
        assertEquals(1, datesToNotify(past, setOf(Triple("a1", "expiry", "2026-01-01"))).size)
        assertTrue(datesToNotify(past, setOf(Triple("a1", "expiry", "2026-03-03"))).isEmpty())
    }

    @Test
    fun archivedRowsAreIgnored() {
        val past = pastDates(
            listOf(date()),
            listOf(article("a1", "2026-01-01").copy(archived = true), article("a2")),
            listOf(item("i1", "a2", "2026-01-01").copy(archived = true), item("i2", "a1", "2026-01-01")),
            today,
        )
        assertTrue(past.isEmpty())
    }

    @Test
    fun anItemDateFlagsAnArticleWithoutValues() {
        val past = pastDates(listOf(date()), listOf(article("a1")), listOf(item("i1", "a1", "2026-01-01")), today)
        assertEquals(setOf("a1"), flaggedArticleIds(past))
    }

    @Test
    fun staleNoticesAreThoseOfGoneOrChangedDatesOnly() {
        val current = mapOf(("a1" to "expiry") to "2026-03-03", ("a2" to "expiry") to "2030-01-01")
        val notified = setOf(
            Triple("a1", "expiry", "2026-03-03"), // same date
            Triple("a1", "expiry", "2026-01-01"), // date changed
            Triple("gone", "expiry", "2026-03-03"), // target deleted or archived
            Triple("a2", "expiry", "2030-01-01"), // not past any more (flag off): kept
        )
        assertEquals(
            setOf(Triple("a1", "expiry", "2026-01-01"), Triple("gone", "expiry", "2026-03-03")),
            staleNotices(current, notified),
        )
    }

    @Test
    fun currentDateValuesSkipDeletedAndArchivedRows() {
        val values = currentDateValues(
            listOf(article("a1", "2026-03-03"), article("a2", "2026-03-03", deleted = true)),
            listOf(item("i1", "a1", "2026-04-04"), item("i2", "a1", "2026-04-04", deleted = true)),
        )
        assertEquals(mapOf(("a1" to "expiry") to "2026-03-03", ("i1" to "expiry") to "2026-04-04"), values)
    }

    @Test
    fun aUnitDateReplacesThePastArticleDate() {
        val all = pastDates(listOf(date()), listOf(article("a1", "2026-01-01")), listOf(item("u1", "a1", "2027-01-01"), item("u2", "a1", "2028-01-01")), today)
        assertTrue(all.isEmpty())
    }

    @Test
    fun aUnitWithoutItsOwnDateKeepsTheArticleDateInForce() {
        val past = pastDates(listOf(date()), listOf(article("a1", "2026-01-01")), listOf(item("u1", "a1", "2027-01-01"), item("u2", "a1")), today)
        assertEquals(listOf("a1"), past.map { it.targetId })
    }

    @Test
    fun aPastUnitDateIsFlaggedAndHidesThePastArticleDate() {
        val past = pastDates(listOf(date()), listOf(article("a1", "2026-01-01")), listOf(item("u1", "a1", "2026-02-02")), today)
        assertEquals(listOf("u1"), past.map { it.targetId })
    }

    @Test
    fun aQuantityArticleWithoutUnitsUsesItsArticleDate() {
        val past = pastDates(listOf(date()), listOf(article("a1", "2026-01-01")), emptyList(), today)
        assertEquals(listOf("a1"), past.map { it.targetId })
    }

    @Test
    fun lostAndOutOfStockUnitsAreIgnored() {
        val units = listOf(
            item("lost", "a1", "2027-01-01", status = ItemStatus.LOST),
            item("out", "a1", "2026-02-02", locationId = null),
            item("archivedUnit", "a1", "2027-01-01").copy(archived = true),
        )
        // No counting unit left: the article date applies, and the ignored units are never flagged
        val past = pastDates(listOf(date()), listOf(article("a1", "2026-01-01")), units, today)
        assertEquals(listOf("a1"), past.map { it.targetId })
    }

    @Test
    fun anOverrideOnALostUnitDoesNotHideThePastArticleDate() {
        val past = pastDates(listOf(date()), listOf(article("a1", "2026-01-01")), listOf(item("lost", "a1", "2027-01-01", status = ItemStatus.LOST), item("u2", "a1")), today)
        assertEquals(listOf("a1"), past.map { it.targetId })
    }
}
