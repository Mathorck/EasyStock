package ch.mathorck.easystock.backup

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking

/** Rows for the device tests (commonTest fixtures are not visible here). */
object DeviceFixtures {
    const val PHOTO = "images/0f8fad5b-d9cb-469f-a165-70867728950e.jpg"

    fun category(id: String, name: String = id, at: Long = 10, deletedAt: Long? = null) =
        Category(id = id, name = name, createdAt = 1, updatedAt = at, deletedAt = deletedAt)

    fun attribute(id: String, key: String = id, at: Long = 10) =
        AttributeDefinition(id = id, key = key, label = key, dataType = AttributeDataType.TEXT, createdAt = 1, updatedAt = at)

    fun link(id: String, category: String, attribute: String, at: Long = 10) =
        CategoryAttribute(id = id, categoryId = category, attributeId = attribute, createdAt = 1, updatedAt = at)

    fun location(id: String, parent: String? = null, at: Long = 10) =
        Location(id = id, name = id, parentId = parent, createdAt = 1, updatedAt = at)

    fun article(
        id: String,
        sku: String? = null,
        barcode: String? = null,
        category: String? = null,
        image: String? = null,
        at: Long = 10,
        deletedAt: Long? = null,
        tracking: Tracking = Tracking.QUANTITY,
    ) = Article(
        id = id, sku = sku, name = id, categoryId = category, barcode = barcode, imagePath = image,
        tracking = tracking, createdAt = 1, updatedAt = at, deletedAt = deletedAt,
    )

    fun item(id: String, article: String, serial: String? = null, location: String? = null, at: Long = 10) =
        Item(id = id, articleId = article, serialNumber = serial, locationId = location, createdAt = 1, updatedAt = at)

    fun movement(id: String, article: String, location: String, quantity: Long, item: String? = null, at: Long = 10) =
        StockMovement(
            id = id, articleId = article, itemId = item, locationId = location, quantityMilli = quantity,
            reason = MovementReason.PURCHASE, occurredAt = at, createdAt = at,
        )

    /** Every table, a soft-deleted row, a serial item moved twice, a photo. */
    fun sample(): BackupData = BackupData(
        categories = listOf(category("c1", "Tools"), category("c2", "Old", at = 20, deletedAt = 20)),
        attributeDefinitions = listOf(attribute("a1", "color")),
        categoryAttributes = listOf(link("ca1", "c1", "a1")),
        locations = listOf(location("shelf", parent = "room"), location("room"), location("van")),
        articles = listOf(
            article("drill", sku = "D-1", barcode = "04:A2", category = "c1", image = PHOTO, tracking = Tracking.SERIAL),
            article("bolt", sku = "B-1"),
            article("gone", at = 30, deletedAt = 30),
        ),
        items = listOf(item("i1", "drill", serial = "SN1")),
        movements = listOf(
            movement("m1", "drill", "shelf", 1000, item = "i1", at = 5),
            movement("m2", "drill", "shelf", -1000, item = "i1", at = 6),
            movement("m3", "drill", "van", 1000, item = "i1", at = 7),
            movement("m4", "bolt", "shelf", 25_500, at = 8),
        ),
    )

    /** Same content whatever the row order. */
    fun BackupData.sorted(): BackupData = BackupData(
        categories = categories.sortedBy { it.id },
        attributeDefinitions = attributeDefinitions.sortedBy { it.id },
        categoryAttributes = categoryAttributes.sortedBy { it.id },
        locations = locations.sortedBy { it.id },
        articles = articles.sortedBy { it.id },
        items = items.sortedBy { it.id },
        movements = movements.sortedBy { it.id },
    )
}
