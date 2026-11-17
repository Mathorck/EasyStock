package ch.mathorck.easystock.viewmodels.alerts

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.PastDate
import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import ch.mathorck.easystock.enums.attributes.attributeDate
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonElement

/**
 * True when [value] is a valid ISO date strictly before [today] (the local day of the phone): a date is
 * flagged from the day after. Empty, invalid or non-string values are never past.
 */
fun isDatePast(value: JsonElement?, today: LocalDate): Boolean = attributeDate(value)?.let { it < today } == true

/** A unit counts for the date rule when it is in stock: live, not archived, not LOST and in a location. */
private fun Item.countsForDates() = deletedAt == null && !archived && status != ItemStatus.LOST && locationId != null

/**
 * The past dates of the live, not archived [articles] and [items] on the live date [definitions] that flag when past.
 * Only units in stock count (not LOST, with a location). A unit's own date replaces the article date for that unit:
 * the article date counts only if a counting unit has no valid date of its own for the key, or the article has no
 * counting unit at all (quantity articles). Items of a deleted or unknown article are ignored.
 * [today] is injected so the rule stays testable.
 */
fun pastDates(
    definitions: List<AttributeDefinition>,
    articles: List<Article>,
    items: List<Item>,
    today: LocalDate,
): List<PastDate> {
    val watched = definitions.filter { it.deletedAt == null && it.dataType == AttributeDataType.DATE && it.flagWhenPast }
    if (watched.isEmpty()) return emptyList()
    val unitsByArticle = items.filter { it.countsForDates() }.groupBy { it.articleId }
    return buildList {
        for (definition in watched) {
            for (article in articles.filter { it.deletedAt == null && !it.archived }) {
                fun add(targetId: String, isItem: Boolean, value: JsonElement?) {
                    if (!isDatePast(value, today)) return
                    add(
                        PastDate(
                            articleId = article.id,
                            articleName = article.name,
                            targetId = targetId,
                            isItem = isItem,
                            attributeKey = definition.key,
                            label = definition.label,
                            dateValue = attributeDate(value).toString(),
                            notify = definition.notifyWhenPast,
                        ),
                    )
                }
                val units = unitsByArticle[article.id].orEmpty()
                val articleCounts = units.isEmpty() || units.any { attributeDate(it.attributes?.get(definition.key)) == null }
                if (articleCounts) add(article.id, false, article.attributes?.get(definition.key))
                units.forEach { add(it.id, true, it.attributes?.get(definition.key)) }
            }
        }
    }
}

/** Ids of the articles that have a past date of their own or on one of their items. */
fun flaggedArticleIds(past: List<PastDate>): Set<String> = past.mapTo(LinkedHashSet()) { it.articleId }

/**
 * The dates to notify now: definitions with `notify_when_past`, not yet in [notified] (target id, attribute key,
 * date value). A changed date has a new key, so it notifies again.
 */
fun datesToNotify(past: List<PastDate>, notified: Set<Triple<String, String, String>>): List<PastDate> =
    past.filter { it.notify && Triple(it.targetId, it.attributeKey, it.dateValue) !in notified }

/** The local day of the phone, the "today" of the past-date rule. */
fun localToday(clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate = clock.todayIn(timeZone)

/** The past dates of the rows read by [articles] and [items]. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observePastDates(
    definitions: Flow<List<AttributeDefinition>>,
    articles: () -> Flow<List<Article>>,
    items: () -> Flow<List<Item>>,
    today: Flow<LocalDate>,
): Flow<List<PastDate>> = definitions
    .map { list -> list.filter { it.deletedAt == null && it.dataType == AttributeDataType.DATE && it.flagWhenPast } }
    .distinctUntilChanged()
    .flatMapLatest { watched ->
        // No definition flags: no article or item is read at all
        if (watched.isEmpty()) flowOf(emptyList()) else combine(articles(), items(), today) { a, i, day -> pastDates(watched, a, i, day) }
    }

/** Milliseconds from [now] to the next local midnight. */
fun millisUntilNextDay(now: Instant, timeZone: TimeZone): Long {
    val today = now.toLocalDateTime(timeZone).date
    return today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds() - now.toEpochMilliseconds()
}

/** The local day, emitted again at each local midnight, so the badges follow the date while the app is open. */
fun todayFlow(clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<LocalDate> = flow {
    while (true) {
        emit(localToday(clock, timeZone))
        delay(millisUntilNextDay(clock.now(), timeZone))
    }
}

/** Date values of live, not archived articles and items, by (target id, attribute key). */
fun currentDateValues(articles: List<Article>, items: List<Item>): Map<Pair<String, String>, String> = buildMap {
    articles.filter { it.deletedAt == null && !it.archived }.forEach { a ->
        a.attributes?.forEach { (key, v) -> attributeDate(v)?.let { put(a.id to key, it.toString()) } }
    }
    items.filter { it.deletedAt == null && !it.archived }.forEach { i ->
        i.attributes?.forEach { (key, v) -> attributeDate(v)?.let { put(i.id to key, it.toString()) } }
    }
}

/**
 * Notices to delete: those whose target is deleted or archived, or whose date value changed ([current]).
 * A notice stays when the flag is turned off or the date is overridden, so the same date is not notified twice.
 */
fun staleNotices(current: Map<Pair<String, String>, String>, notified: Set<Triple<String, String, String>>): Set<Triple<String, String, String>> =
    notified.filterTo(LinkedHashSet()) { current[it.first to it.second] != it.third }

/** Most lines one date notification keeps: older ones drop first. */
const val MAX_DATE_ALERT_LINES = 50

/** The lines of the notification still shown ([previous]) then the [new] ones, each once, the newest [max] kept. */
fun mergeDateAlertLines(previous: List<String>, new: List<String>, max: Int = MAX_DATE_ALERT_LINES): List<String> =
    (previous + new).distinct().takeLast(max)
