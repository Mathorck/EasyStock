package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.enums.article.Tracking
import ch.mathorck.easystock.enums.stock.MovementReason
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Local edits made through the same DAO methods as the screens: the outbox triggers queue them for the push.

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

fun now(): Long = System.currentTimeMillis()

suspend fun Phone.createCategory(name: String): Category =
    Category(newId(), name, createdAt = now(), updatedAt = now()).also { db.categoryDao().create(it) }

suspend fun Phone.createLocation(name: String, parentId: String? = null): Location =
    db.locationDao().createLocation(newId(), name, parentId, now())

suspend fun Phone.createArticle(
    name: String,
    categoryId: String? = null,
    tracking: Tracking = Tracking.QUANTITY,
    imagePath: String? = null,
): Article = Article(
    id = newId(), name = name, categoryId = categoryId, tracking = tracking, imagePath = imagePath,
    createdAt = now(), updatedAt = now(),
).also { db.articleDao().insert(it) }

/** A stock entry of [quantity] units (thousandths in the database). */
suspend fun Phone.stockIn(articleId: String, locationId: String, quantity: Long): StockMovement =
    movement(articleId, locationId, quantity * 1000, MovementReason.PURCHASE, now()).also { db.stockActionDao().adjust(it) }

/** Both rows of a transfer share one timestamp, as the DAO requires (a clock read per row can differ by 1 ms). */
suspend fun Phone.transfer(articleId: String, from: String, to: String, quantity: Long) {
    val at = now()
    db.stockActionDao().transfer(
        movement(articleId, from, -quantity * 1000, MovementReason.TRANSFER, at),
        movement(articleId, to, quantity * 1000, MovementReason.TRANSFER, at),
    )
}

suspend fun Phone.addUnit(articleId: String, locationId: String, serial: String): Item {
    val at = now()
    val item = Item(newId(), articleId, serialNumber = serial, locationId = locationId, createdAt = at, updatedAt = at)
    db.stockActionDao().addItem(item, movement(articleId, locationId, 1000, MovementReason.PURCHASE, at, item.id))
    return item
}

/** [at] is passed in, not read here, so the rows of one action (a transfer pair) can share it. */
private fun movement(articleId: String, locationId: String, milli: Long, reason: MovementReason, at: Long, itemId: String? = null) =
    StockMovement(newId(), articleId, itemId, locationId, milli, reason, occurredAt = at, createdAt = at)

/**
 * The synced rows of a phone, comparable with another phone's: the photo file name is chosen by each
 * phone, so only its presence is kept. Everything else must match, the author of a movement too.
 */
fun BackupData.comparable(): BackupData = copy(
    articles = articles.map { it.copy(imagePath = it.imagePath?.let { "photo" }) },
)

/** Same synced rows on both phones; the message lists only the rows that differ. */
fun assertSameData(expected: BackupData, actual: BackupData) {
    val x = expected.comparable()
    val y = actual.comparable()
    if (x == y) return
    val tables = listOf(
        "category" to (x.categories to y.categories),
        "attribute_definition" to (x.attributeDefinitions to y.attributeDefinitions),
        "category_attribute" to (x.categoryAttributes to y.categoryAttributes),
        "location" to (x.locations to y.locations),
        "article" to (x.articles to y.articles),
        "item" to (x.items to y.items),
        "stock_movement" to (x.movements to y.movements),
        "stock_review" to (x.reviews to y.reviews),
        "stock_review_line" to (x.reviewLines to y.reviewLines),
    )
    val diff = tables.joinToString("\n") { (table, rows) ->
        val (a, b) = rows
        "$table: only expected ${a - b.toSet()}, only actual ${b - a.toSet()}"
    }
    throw AssertionError("Synced data differs:\n$diff")
}
