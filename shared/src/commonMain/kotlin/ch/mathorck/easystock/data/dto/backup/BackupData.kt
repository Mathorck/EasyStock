package ch.mathorck.easystock.data.dto.backup

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
import ch.mathorck.easystock.enums.backup.BackupTable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every row of every synced table, soft-deleted rows included. The server mode tables (`sync_state`,
 * `outbox`, `server_user`, `low_stock_alert`, `rejected_change`) belong to this phone and are never
 * part of a backup.
 */
@Serializable
data class BackupData(
    @SerialName("category") val categories: List<Category> = emptyList(),
    @SerialName("attribute_definition") val attributeDefinitions: List<AttributeDefinition> = emptyList(),
    @SerialName("category_attribute") val categoryAttributes: List<CategoryAttribute> = emptyList(),
    @SerialName("location") val locations: List<Location> = emptyList(),
    @SerialName("article") val articles: List<Article> = emptyList(),
    /** Schema 13+. Older backups have none. */
    @SerialName("article_flag") val articleFlags: List<ArticleFlag> = emptyList(),
    @SerialName("item") val items: List<Item> = emptyList(),
    @SerialName("stock_movement") val movements: List<StockMovement> = emptyList(),
    /** Schema 5+. Older backups have none. */
    @SerialName("stock_review") val reviews: List<StockReview> = emptyList(),
    @SerialName("stock_review_line") val reviewLines: List<StockReviewLine> = emptyList(),
) {
    fun counts(): Map<String, Int> = mapOf(
        BackupTable.CATEGORY.tableName to categories.size,
        BackupTable.ATTRIBUTE_DEFINITION.tableName to attributeDefinitions.size,
        BackupTable.CATEGORY_ATTRIBUTE.tableName to categoryAttributes.size,
        BackupTable.LOCATION.tableName to locations.size,
        BackupTable.ARTICLE.tableName to articles.size,
        BackupTable.ARTICLE_FLAG.tableName to articleFlags.size,
        BackupTable.ITEM.tableName to items.size,
        BackupTable.STOCK_MOVEMENT.tableName to movements.size,
        BackupTable.STOCK_REVIEW.tableName to reviews.size,
        BackupTable.STOCK_REVIEW_LINE.tableName to reviewLines.size,
    )

    /** Photo files referenced by the articles, deleted articles included (photos are kept on soft delete). */
    fun imagePaths(): Set<String> = articles.mapNotNull { it.imagePath }.toSet()

    val isEmpty: Boolean get() = counts().values.all { it == 0 }
}
