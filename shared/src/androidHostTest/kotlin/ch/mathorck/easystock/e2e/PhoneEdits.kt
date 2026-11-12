package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking
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
    movement(articleId, locationId, quantity * 1000, MovementReason.PURCHASE).also { db.stockActionDao().adjust(it) }

suspend fun Phone.transfer(articleId: String, from: String, to: String, quantity: Long) {
    db.stockActionDao().transfer(
        movement(articleId, from, -quantity * 1000, MovementReason.TRANSFER),
        movement(articleId, to, quantity * 1000, MovementReason.TRANSFER),
    )
}

suspend fun Phone.addUnit(articleId: String, locationId: String, serial: String): Item {
    val item = Item(newId(), articleId, serialNumber = serial, locationId = locationId, createdAt = now(), updatedAt = now())
    db.stockActionDao().addItem(item, movement(articleId, locationId, 1000, MovementReason.PURCHASE, item.id))
    return item
}

private fun movement(articleId: String, locationId: String, milli: Long, reason: MovementReason, itemId: String? = null) =
    StockMovement(newId(), articleId, itemId, locationId, milli, reason, occurredAt = now(), createdAt = now())

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
