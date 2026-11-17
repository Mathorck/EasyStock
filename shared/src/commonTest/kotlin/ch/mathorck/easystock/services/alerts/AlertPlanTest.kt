package ch.mathorck.easystock.services.alerts

import ch.mathorck.easystock.data.model.alerts.LowStockAlert
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.ArticleStock
import ch.mathorck.easystock.enums.article.Tracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

internal const val NOW = 1_800_000_000_000L

private val DAY = 1.days.inWholeMilliseconds

internal fun alert(id: String, articleId: String, crossedAt: Long = NOW - 1000) = LowStockAlert(id, articleId, crossedAt, 0)

internal fun stock(
    id: String,
    stockMilli: Long,
    threshold: Long? = 5000,
    deleted: Boolean = false,
    archived: Boolean = false,
    tracking: Tracking = Tracking.QUANTITY,
) = ArticleStock(
    Article(
        id = id, name = "Article $id", unit = "kg", tracking = tracking, thresholdMilli = threshold, archived = archived,
        createdAt = 0, updatedAt = 0, deletedAt = if (deleted) 1 else null,
    ),
    stockMilli,
)

private fun plan(
    pending: List<LowStockAlert>,
    articles: List<ArticleStock>,
    shown: List<ShownAlert> = emptyList(),
    canReceive: Boolean = true,
) = planAlerts(pending, shown, articles.associateBy { it.article.id }, canReceive, NOW)

class AlertPlanTest {
    @Test
    fun aLowArticleIsNotifiedWithItsCurrentStock() {
        val result = plan(listOf(alert("al1", "a1")), listOf(stock("a1", 2500)))
        assertEquals(listOf(AlertNotice("a1", "Article a1", 2500, "kg")), result.single)
        assertNull(result.group)
        assertEquals(listOf("al1"), result.markNotified)
    }

    @Test
    fun aResolvedArticleIsMarkedWithoutANotification() {
        val result = plan(listOf(alert("al1", "a1")), listOf(stock("a1", 6000)))
        assertTrue(result.single.isEmpty())
        assertEquals(listOf("al1"), result.markNotified)
    }

    @Test
    fun deletedArchivedSerialAndNoThresholdNeverNotify() {
        val articles = listOf(
            stock("a1", 0, deleted = true),
            stock("a2", 0, archived = true),
            stock("a3", 0, tracking = Tracking.SERIAL),
            stock("a4", 0, threshold = null),
        )
        val result = plan(articles.map { alert("al-${it.article.id}", it.article.id) }, articles)
        assertTrue(result.single.isEmpty())
        assertNull(result.group)
        assertEquals(4, result.markNotified.size)
    }

    @Test
    fun anArticleNotPulledYetWaits() {
        val result = plan(listOf(alert("al1", "missing")), emptyList())
        assertTrue(result.single.isEmpty())
        assertTrue(result.markNotified.isEmpty())
    }

    @Test
    fun anArticleThatNeverArrivesIsDroppedAfterTheMaxAge() {
        val result = plan(listOf(alert("al1", "missing", crossedAt = NOW - 8 * DAY)), emptyList())
        assertEquals(listOf("al1"), result.markNotified)
    }

    @Test
    fun anOldAlertIsMarkedWithoutANotification() {
        // The full pull after joining brings every past alert
        val result = plan(listOf(alert("al1", "a1", crossedAt = NOW - 8 * DAY)), listOf(stock("a1", 0)))
        assertTrue(result.single.isEmpty())
        assertEquals(listOf("al1"), result.markNotified)
    }

    @Test
    fun severalAlertsOfOneArticleMakeOneNotification() {
        val result = plan(listOf(alert("al1", "a1", NOW - 5000), alert("al2", "a1")), listOf(stock("a1", 1000)))
        assertEquals(1, result.single.size)
        assertEquals(setOf("al1", "al2"), result.markNotified.toSet())
    }

    @Test
    fun threeArticlesGetOneNotificationEach() {
        val articles = (1..3).map { stock("a$it", 0) }
        val result = plan(articles.map { alert("al-${it.article.id}", it.article.id) }, articles)
        assertEquals(3, result.single.size)
        assertNull(result.group)
    }

    @Test
    fun moreThanThreeArticlesGetOneGroupedNotificationOldestFirst() {
        val articles = (1..4).map { stock("a$it", 0) }
        val alerts = articles.mapIndexed { i, a -> alert("al$i", a.article.id, crossedAt = NOW - (10 - i) * 1000L) }
        val result = plan(alerts.reversed(), articles)
        assertTrue(result.single.isEmpty())
        assertEquals(listOf("a1", "a2", "a3", "a4"), result.group?.map { it.articleId })
        assertEquals(4, result.markNotified.size)
    }

    @Test
    fun aRoleWithoutAlertsShowsNothingAndClearsTheShownOnes() {
        val result = plan(
            listOf(alert("al1", "a1")),
            listOf(stock("a1", 0)),
            shown = listOf(ShownAlert("a2", setOf("a2"))),
            canReceive = false,
        )
        assertTrue(result.single.isEmpty())
        assertEquals(listOf("al1"), result.markNotified)
        assertEquals(listOf("a2"), result.cancel)
    }

    @Test
    fun aShownNotificationIsRemovedOnceItsArticleIsNoLongerLow() {
        val shown = listOf(
            ShownAlert("a1", setOf("a1")),
            ShownAlert("a2", setOf("a2")),
            ShownAlert("a3", setOf("a3")),
        )
        val result = plan(emptyList(), listOf(stock("a1", 6000), stock("a2", 1000)), shown)
        // a3 is gone from the phone: nothing left to alert about
        assertEquals(listOf("a1", "a3"), result.cancel)
    }

    @Test
    fun aGroupStaysWhileOneOfItsArticlesIsLow() {
        val group = ShownAlert(GROUP_KEY, setOf("a1", "a2"))
        assertTrue(plan(emptyList(), listOf(stock("a1", 6000), stock("a2", 0)), listOf(group)).cancel.isEmpty())
        assertEquals(listOf(GROUP_KEY), plan(emptyList(), listOf(stock("a1", 6000), stock("a2", 6000)), listOf(group)).cancel)
    }
}
