package ch.mathorck.easystock.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.flow.Flow

/** An article with the name of its category (null without category). */
data class ArticleWithCategory(
    @Embedded val article: Article,
    @ColumnInfo(name = "category_name") val categoryName: String?,
)

/** Stock of one article at one location, in thousandths of the article unit. */
data class LocationStock(
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "stock") val stockMilli: Long,
)

/** A movement with the name of its location. */
data class MovementWithLocation(
    @Embedded val movement: StockMovement,
    @ColumnInfo(name = "location_name") val locationName: String,
)

/** The SKU of an article, deleted ones included. */
data class SkuOwner(val id: String, val sku: String?)

/** Edited fields of an article. Everything else is kept from the stored row. */
data class ArticleChanges(
    val name: String,
    val sku: String?,
    val description: String?,
    val categoryId: String?,
    val unit: String,
    val thresholdMilli: Long?,
)

/** The article was deleted (or never existed) when saving. */
class ArticleNotFoundException(val id: String) : IllegalStateException("No active article: $id")

/** Reads for the article detail screen, and the article edit. Movements are read only. */
@Dao
abstract class ArticleDetailDao {
    /** The active article with its category name (also when the category is soft-deleted). */
    @Query(
        """
        SELECT article.*, category.name AS category_name
        FROM article
        LEFT JOIN category ON category.id = article.category_id
        WHERE article.id = :id AND article.deleted_at IS NULL
        """,
    )
    abstract fun observeArticle(id: String): Flow<ArticleWithCategory?>

    /** Stock per location for the article, locations with a zero sum left out. */
    @Query(
        """
        SELECT location_id, SUM(quantity) AS stock
        FROM stock_movement
        WHERE article_id = :articleId
        GROUP BY location_id
        HAVING SUM(quantity) != 0
        """,
    )
    abstract fun observeStockByLocation(articleId: String): Flow<List<LocationStock>>

    /** Movements of the article, newest first. Soft-deleted locations are included. */
    @Query(
        """
        SELECT stock_movement.*, location.name AS location_name
        FROM stock_movement
        JOIN location ON location.id = stock_movement.location_id
        WHERE stock_movement.article_id = :articleId
        ORDER BY stock_movement.occurred_at DESC, stock_movement.created_at DESC, stock_movement.id DESC
        """,
    )
    abstract fun observeMovements(articleId: String): Flow<List<MovementWithLocation>>

    /** All locations, soft-deleted ones included, so old stock and movements keep their path. */
    @Query("SELECT * FROM location")
    abstract fun observeAllLocations(): Flow<List<Location>>

    /** SKUs of all articles, soft-deleted ones included (the unique index covers them). */
    @Query("SELECT id, sku FROM article WHERE sku IS NOT NULL")
    abstract fun observeSkus(): Flow<List<SkuOwner>>

    @Query("SELECT * FROM article WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun getActiveArticle(id: String): Article?

    /** Ignores case. Includes soft-deleted articles: they keep their SKU and the unique index. */
    @Query("SELECT COUNT(*) FROM article WHERE sku = :sku COLLATE NOCASE AND id != :exceptId")
    protected abstract suspend fun countOtherArticlesWithSku(sku: String, exceptId: String): Int

    /** Ignores case. Includes soft-deleted categories: they keep their name and the unique index. */
    @Query("SELECT * FROM category WHERE name = :name COLLATE NOCASE LIMIT 1")
    protected abstract suspend fun findCategoryByName(name: String): Category?

    @Insert
    protected abstract suspend fun insertCategory(category: Category)

    @Update
    protected abstract suspend fun updateRow(article: Article)

    /**
     * Applies [changes] to the stored article and sets `updated_at` to [now], in one transaction.
     * [newCategory] is created first and replaces [ArticleChanges.categoryId], unless an active
     * category already has its name (ignoring case): that one is reused.
     * Throws [ArticleNotFoundException], [DuplicateSkuException] or [DeletedCategoryNameException];
     * nothing is saved then.
     */
    @Transaction
    open suspend fun updateArticle(id: String, changes: ArticleChanges, newCategory: Category?, now: Long) {
        val stored = getActiveArticle(id) ?: throw ArticleNotFoundException(id)
        changes.sku?.let { if (countOtherArticlesWithSku(it, id) > 0) throw DuplicateSkuException(it) }
        var categoryId = changes.categoryId
        newCategory?.let { category ->
            val existing = findCategoryByName(category.name)
            categoryId = when {
                existing == null -> category.id.also { insertCategory(category) }
                existing.deletedAt != null -> throw DeletedCategoryNameException(category.name)
                else -> existing.id
            }
        }
        updateRow(
            stored.copy(
                name = changes.name,
                sku = changes.sku,
                description = changes.description,
                categoryId = categoryId,
                unit = changes.unit,
                thresholdMilli = changes.thresholdMilli,
                updatedAt = now,
            ),
        )
    }
}
