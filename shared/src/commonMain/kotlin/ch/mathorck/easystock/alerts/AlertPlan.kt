package ch.mathorck.easystock.alerts

import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.LowStockAlert
import kotlin.time.Duration.Companion.days

/** Key of the grouped notification (more than [MAX_SINGLE_NOTICES] articles in one sync). */
const val GROUP_KEY = "low-stock-group"

/** Up to this many articles get one notification each, more get one grouped notification. */
const val MAX_SINGLE_NOTICES = 3

/** An alert older than this is never shown (the full pull after joining brings every past alert). */
val MAX_ALERT_AGE = 7.days

/** What a low-stock notification shows: the article and its current stock on this phone. */
data class AlertNotice(
    val articleId: String,
    val articleName: String,
    /** Current stock, in thousandths of [unit]. */
    val stockMilli: Long,
    val unit: String,
)

/** A notification still shown on the phone: [key] is the article id, or [GROUP_KEY]. */
data class ShownAlert(val key: String, val articleIds: Set<String>)

/** What to do after a sync. */
data class AlertPlan(
    /** One notification each (key: the article id). */
    val single: List<AlertNotice> = emptyList(),
    /** One grouped notification ([GROUP_KEY]) with these articles, null for none. */
    val group: List<AlertNotice>? = null,
    /** Alerts never to show again: shown now, resolved, too old, or not for this role. */
    val markNotified: List<String> = emptyList(),
    /** Notifications to remove: all their articles are no longer low. */
    val cancel: List<String> = emptyList(),
)

/**
 * True when the article still deserves an alert: active, not archived, and at or below its
 * threshold now (serial articles never). The server sends no "resolved" signal: the local stock decides.
 */
val ArticleStock.isAlerting: Boolean
    get() = article.deletedAt == null && !article.archived && isLowStock

/**
 * Decides which [pending] alerts (not yet notified) to show, after a sync.
 * - [canReceive] false (Guest, unknown role): nothing is shown, the alerts are marked and the shown notifications removed.
 * - An article not on the phone yet waits for a later sync, up to [MAX_ALERT_AGE].
 * - An article no longer low (resolved, deleted, archived) or an alert older than [MAX_ALERT_AGE] is marked silently.
 * - Several alerts of one article make one notification, with the current stock.
 * - More than [MAX_SINGLE_NOTICES] articles: one grouped notification.
 * - A [shown] notification whose articles are all no longer low is removed.
 * [articles]: the articles of [pending] and [shown] with their total stock, deleted ones included.
 */
fun planAlerts(
    pending: List<LowStockAlert>,
    shown: List<ShownAlert>,
    articles: Map<String, ArticleStock>,
    canReceive: Boolean,
    now: Long,
): AlertPlan {
    if (!canReceive) return AlertPlan(markNotified = pending.map { it.id }, cancel = shown.map { it.key })
    val oldest = now - MAX_ALERT_AGE.inWholeMilliseconds
    val mark = mutableListOf<String>()
    val notices = mutableListOf<Pair<Long, AlertNotice>>()
    pending.groupBy { it.articleId }.forEach { (articleId, alerts) ->
        val latest = alerts.maxOf { it.crossedAt }
        val article = articles[articleId]
        if (article == null) {
            // Not pulled yet (no foreign key): shown once it arrives, unless it never does
            if (latest < oldest) mark += alerts.map { it.id }
            return@forEach
        }
        mark += alerts.map { it.id }
        if (latest >= oldest && article.isAlerting) {
            notices += latest to AlertNotice(articleId, article.article.name, article.stockMilli, article.article.unit)
        }
    }
    val ordered = notices.sortedBy { it.first }.map { it.second }
    val cancel = shown.filter { notice -> notice.articleIds.none { articles[it]?.isAlerting == true } }.map { it.key }
    return if (ordered.size > MAX_SINGLE_NOTICES) {
        AlertPlan(group = ordered, markNotified = mark, cancel = cancel)
    } else {
        AlertPlan(single = ordered, markNotified = mark, cancel = cancel)
    }
}
