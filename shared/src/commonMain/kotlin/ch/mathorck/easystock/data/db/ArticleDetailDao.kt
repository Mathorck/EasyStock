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
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

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

/**
 * A movement with the name of its location, and the display name of its author (server mode only:
 * null when `created_by` is null, always in standalone mode, or when the user is not in `server_user`).
 */
data class MovementWithLocation(
    @Embedded val movement: StockMovement,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "author_name") val authorName: String? = null,
)

/**
 * A review line of the article flagged "info to fix", from a finished review. Open while [fixedAt] is null.
 * [serialNumber] is set for a serial unit line.
 */
data class InfoFlag(
    @ColumnInfo(name = "line_id") val lineId: String,
    val note: String?,
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    /** When the review was finished: the flag is known from then on. */
    @ColumnInfo(name = "finished_at") val flaggedAt: Long,
    @ColumnInfo(name = "info_fixed_at") val fixedAt: Long?,
)

/** Location of the last movement of a serial unit. */
data class ItemLastLocation(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "location_id") val locationId: String,
)

/** The SKU of an article, deleted ones included. */
data class SkuOwner(val id: String, val sku: String?)

/** Edited fields of an article. Everything else is kept from the stored row. */
data class ArticleChanges(
    val name: String,
    /** The name follows the category template. */
    val nameAuto: Boolean,
    val sku: String?,
    val description: String?,
    val categoryId: String?,
    val unit: String,
    val thresholdMilli: Long?,
    /** Photo path, see [ch.mathorck.easystock.images.newImagePath]. */
    val imagePath: String?,
    /** Custom attribute values, keyed by attribute key. Replaces the stored object. */
    val attributes: JsonObject?,
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

    /** Movements of the article, newest first, with their author's name. Soft-deleted locations are included. */
    @Query(
        """
        SELECT stock_movement.*, location.name AS location_name, server_user.display_name AS author_name
        FROM stock_movement
        JOIN location ON location.id = stock_movement.location_id
        LEFT JOIN server_user ON server_user.user_id = stock_movement.created_by
        WHERE stock_movement.article_id = :articleId
        ORDER BY stock_movement.occurred_at DESC, stock_movement.created_at DESC, stock_movement.id DESC
        """,
    )
    abstract fun observeMovements(articleId: String): Flow<List<MovementWithLocation>>

    /**
     * Lines of the article flagged "info to fix" in a finished review, fixed ones included (for the history).
     * Open and cancelled reviews, soft-deleted reviews and lines are left out. Newest review first.
     */
    @Query(
        """
        SELECT l.id AS line_id, l.note, l.location_id, location.name AS location_name, item.serial_number,
            r.finished_at, l.info_fixed_at
        FROM stock_review_line l
        JOIN stock_review r ON r.id = l.review_id
        JOIN location ON location.id = l.location_id
        LEFT JOIN item ON item.id = l.item_id
        WHERE l.article_id = :articleId AND l.info_flagged = 1 AND l.deleted_at IS NULL
          AND r.status = 'finished' AND r.deleted_at IS NULL AND r.finished_at IS NOT NULL
        ORDER BY r.finished_at DESC, l.created_at DESC, l.id DESC
        """,
    )
    abstract fun observeInfoFlags(articleId: String): Flow<List<InfoFlag>>

    /**
     * Marks the flagged info of the line as fixed at [now] (also `updated_at`, so a backup merge keeps it).
     * Only an open flag of a finished review: returns false and changes nothing otherwise.
     */
    @Query(
        """
        UPDATE stock_review_line SET info_fixed_at = :now, updated_at = :now
        WHERE id = :lineId AND info_flagged = 1 AND info_fixed_at IS NULL AND deleted_at IS NULL
          AND review_id IN (SELECT id FROM stock_review WHERE status = 'finished' AND deleted_at IS NULL)
        """,
    )
    protected abstract suspend fun markInfoFixedRow(lineId: String, now: Long): Int

    /** See [markInfoFixedRow]. */
    open suspend fun markInfoFixed(lineId: String, now: Long): Boolean = markInfoFixedRow(lineId, now) > 0

    /** All locations, soft-deleted ones included, so old stock and movements keep their path. */
    @Query("SELECT * FROM location")
    abstract fun observeAllLocations(): Flow<List<Location>>

    /** Active serial units of the article, sorted by serial number (units without one last). */
    @Query(
        """
        SELECT * FROM item
        WHERE article_id = :articleId AND deleted_at IS NULL
        ORDER BY serial_number IS NULL, serial_number COLLATE NOCASE, created_at
        """,
    )
    abstract fun observeItems(articleId: String): Flow<List<Item>>

    /**
     * Location of the last movement of each unit of the article: where a lent or lost unit was last,
     * used as the default location when it comes back.
     */
    @Query(
        """
        SELECT m.item_id AS item_id, m.location_id AS location_id
        FROM stock_movement m
        WHERE m.article_id = :articleId AND m.item_id IS NOT NULL
          AND m.id = (
            SELECT latest.id FROM stock_movement latest
            WHERE latest.item_id = m.item_id
            ORDER BY latest.occurred_at DESC, latest.created_at DESC, latest.quantity DESC, latest.id DESC
            LIMIT 1
          )
        """,
    )
    abstract fun observeLastItemLocations(articleId: String): Flow<List<ItemLastLocation>>

    /** Serial numbers of all items, soft-deleted ones included (the unique index covers them). */
    @Query("SELECT serial_number FROM item WHERE serial_number IS NOT NULL")
    abstract fun observeSerials(): Flow<List<String>>

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

    @Query("UPDATE article SET image_path = :path, updated_at = :now WHERE id = :id")
    protected abstract suspend fun updateImagePathRow(id: String, path: String?, now: Long)

    /**
     * Sets (or clears, with null) the photo of the active article and returns the previous path,
     * so the caller can delete the old file. `updated_at` only changes when the path does.
     * Throws [ArticleNotFoundException].
     */
    @Transaction
    open suspend fun setImagePath(id: String, path: String?, now: Long): String? {
        val stored = getActiveArticle(id) ?: throw ArticleNotFoundException(id)
        if (stored.imagePath != path) updateImagePathRow(id, path, now)
        return stored.imagePath
    }

    /**
     * Applies [changes] to the stored article and sets `updated_at` to [now], in one transaction.
     * [newCategory] is created first and replaces [ArticleChanges.categoryId], unless an active
     * category already has its name (ignoring case): that one is reused.
     * Throws [ArticleNotFoundException], [DuplicateSkuException] or [DeletedCategoryNameException];
     * nothing is saved then.
     * Returns the photo path stored before, so the caller can delete a replaced file.
     */
    @Transaction
    open suspend fun updateArticle(id: String, changes: ArticleChanges, newCategory: Category?, now: Long): String? {
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
                nameAuto = changes.nameAuto,
                sku = changes.sku,
                description = changes.description,
                categoryId = categoryId,
                unit = changes.unit,
                thresholdMilli = changes.thresholdMilli,
                imagePath = changes.imagePath,
                attributes = changes.attributes,
                updatedAt = now,
            ),
        )
        return stored.imagePath
    }
}
