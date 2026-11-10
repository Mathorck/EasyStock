package ch.mathorck.easystock.backup

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.ReviewResult
import ch.mathorck.easystock.data.model.ReviewStatus
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Small rows for backup tests. Timestamps are plain numbers, the merge only compares them. */
object BackupFixtures {
    fun category(id: String, name: String = id, at: Long = 10, deletedAt: Long? = null) =
        Category(id = id, name = name, createdAt = 1, updatedAt = at, deletedAt = deletedAt)

    fun attribute(id: String, key: String = id, label: String = key, at: Long = 10) =
        AttributeDefinition(id = id, key = key, label = label, dataType = AttributeDataType.TEXT, createdAt = 1, updatedAt = at)

    fun link(id: String, category: String, attribute: String, at: Long = 10) =
        CategoryAttribute(id = id, categoryId = category, attributeId = attribute, createdAt = 1, updatedAt = at)

    fun location(id: String, parent: String? = null, at: Long = 10) =
        Location(id = id, name = id, parentId = parent, createdAt = 1, updatedAt = at)

    fun article(
        id: String,
        name: String = id,
        sku: String? = null,
        barcode: String? = null,
        category: String? = null,
        image: String? = null,
        at: Long = 10,
        deletedAt: Long? = null,
        tracking: Tracking = Tracking.QUANTITY,
    ) = Article(
        id = id, sku = sku, name = name, categoryId = category, barcode = barcode, imagePath = image,
        tracking = tracking, createdAt = 1, updatedAt = at, deletedAt = deletedAt,
    )

    fun item(id: String, article: String, serial: String? = null, location: String? = null, at: Long = 10) =
        Item(id = id, articleId = article, serialNumber = serial, locationId = location, createdAt = 1, updatedAt = at)

    fun movement(id: String, article: String, location: String, quantity: Long, item: String? = null, at: Long = 10) =
        StockMovement(
            id = id, articleId = article, itemId = item, locationId = location, quantityMilli = quantity,
            reason = MovementReason.PURCHASE, occurredAt = at, createdAt = at,
        )

    fun review(id: String, location: String, at: Long = 10, status: ReviewStatus = ReviewStatus.OPEN) =
        StockReview(id = id, locationId = location, status = status, startedAt = 1, createdAt = 1, updatedAt = at)

    fun reviewLine(id: String, review: String, article: String, location: String, item: String? = null, at: Long = 10) =
        StockReviewLine(
            id = id, reviewId = review, articleId = article, itemId = item, locationId = location,
            expectedMilli = 1000, createdAt = 1, updatedAt = at,
        )

    /** One row of every kind, every column set, with a soft-deleted row. */
    fun full(): BackupData = BackupData(
        categories = listOf(category("c1", "Tools"), category("c2", "Old", deletedAt = 20, at = 20)),
        attributeDefinitions = listOf(
            AttributeDefinition(
                id = "a1", key = "color", label = "Color", dataType = AttributeDataType.ENUM, unit = "mm",
                options = listOf("red", "blue"), createdAt = 1, updatedAt = 2,
            ),
        ),
        categoryAttributes = listOf(CategoryAttribute(id = "ca1", categoryId = "c1", attributeId = "a1", required = true, sortOrder = 3, createdAt = 1, updatedAt = 2)),
        locations = listOf(location("room"), location("shelf", parent = "room")),
        articles = listOf(
            Article(
                id = "art1", sku = "S-1", name = "Drill \"Ünïcode\" ✓", description = "line1\nline2", categoryId = "c1",
                tracking = Tracking.SERIAL, unit = "pce", barcode = "04:A2:3B", thresholdMilli = 1500,
                imagePath = "images/0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
                attributes = buildJsonObject { put("color", JsonPrimitive("red")); put("length", JsonPrimitive(20)) },
                archived = true, createdAt = 1, updatedAt = 2,
            ),
            article("art2", deletedAt = 30, at = 30),
        ),
        items = listOf(
            Item(
                id = "i1", articleId = "art1", serialNumber = "SN1", status = ItemStatus.LENT, locationId = null,
                attributes = buildJsonObject { put("color", JsonPrimitive("blue")) }, archived = false, createdAt = 1, updatedAt = 2,
            ),
        ),
        movements = listOf(
            StockMovement(
                id = "m1", articleId = "art1", itemId = "i1", locationId = "shelf", quantityMilli = 1000,
                reason = MovementReason.PURCHASE, comment = "first", occurredAt = 5, createdAt = 6,
            ),
            StockMovement(
                id = "m2", articleId = "art1", itemId = "i1", locationId = "shelf", quantityMilli = -1000,
                reason = MovementReason.LOAN, occurredAt = 7, createdAt = 8,
            ),
        ),
        reviews = listOf(
            StockReview(
                id = "r1", locationId = "room", status = ReviewStatus.FINISHED, startedAt = 3, finishedAt = 9,
                createdAt = 3, updatedAt = 9,
            ),
        ),
        reviewLines = listOf(
            StockReviewLine(
                id = "rl1", reviewId = "r1", articleId = "art1", itemId = "i1", locationId = "shelf",
                expectedMilli = 1000, countedMilli = null, result = ReviewResult.ELSEWHERE, foundLocationId = "room",
                infoFlagged = true, note = "label faded", checkedAt = 4, createdAt = 3, updatedAt = 4, deletedAt = null,
            ),
            StockReviewLine(
                id = "rl2", reviewId = "r1", articleId = "art2", locationId = "room", expectedMilli = 2500,
                countedMilli = 2000, result = ReviewResult.COUNTED, checkedAt = 4, createdAt = 3, updatedAt = 4,
            ),
        ),
    )
}
