package ch.mathorck.easystock.enums.sync

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.ArticleFlag
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.CategoryAttribute
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.review.StockReview
import ch.mathorck.easystock.data.model.review.StockReviewLine
import ch.mathorck.easystock.data.model.stock.StockMovement
import kotlinx.serialization.KSerializer

/** The 10 synced tables, in dependency order (parents first), with their entity and references. */
enum class SyncTable(val tableName: String, internal val serializer: KSerializer<*>, val references: Map<String, String> = emptyMap()) {
    CATEGORY("category", Category.serializer()),
    ATTRIBUTE_DEFINITION("attribute_definition", AttributeDefinition.serializer()),
    CATEGORY_ATTRIBUTE(
        "category_attribute",
        CategoryAttribute.serializer(),
        mapOf("category_id" to "category", "attribute_id" to "attribute_definition"),
    ),
    LOCATION("location", Location.serializer(), mapOf("parent_id" to "location")),
    ARTICLE("article", Article.serializer(), mapOf("category_id" to "category")),
    ARTICLE_FLAG("article_flag", ArticleFlag.serializer(), mapOf("article_id" to "article")),
    ITEM("item", Item.serializer(), mapOf("article_id" to "article", "location_id" to "location")),
    STOCK_MOVEMENT(
        "stock_movement",
        StockMovement.serializer(),
        mapOf("article_id" to "article", "item_id" to "item", "location_id" to "location"),
    ),
    STOCK_REVIEW("stock_review", StockReview.serializer(), mapOf("location_id" to "location")),
    STOCK_REVIEW_LINE(
        "stock_review_line",
        StockReviewLine.serializer(),
        mapOf(
            "review_id" to "stock_review",
            "article_id" to "article",
            "item_id" to "item",
            "location_id" to "location",
            "found_location_id" to "location",
        ),
    ),
    ;

    companion object {
        private val byName = entries.associateBy { it.tableName }

        fun of(tableName: String): SyncTable? = byName[tableName]
    }
}
