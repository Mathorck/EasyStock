package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import kotlinx.coroutines.flow.Flow

/** Reads ignore soft-deleted articles. There is no hard delete. */
@Dao
abstract class ArticleDao {
    /** Fails if the sku or barcode is already used. */
    @Insert
    abstract suspend fun insert(article: Article)

    /** The caller sets [Article.updatedAt]. */
    @Update
    abstract suspend fun update(article: Article)

    @Query("SELECT * FROM article WHERE id = :id AND deleted_at IS NULL")
    abstract suspend fun getById(id: String): Article?

    /** Article for a scanned NFC tag. */
    @Query("SELECT * FROM article WHERE barcode = :barcode AND deleted_at IS NULL")
    abstract suspend fun findByBarcode(barcode: String): Article?

    @Query("SELECT * FROM article WHERE deleted_at IS NULL ORDER BY name")
    abstract fun observeAll(): Flow<List<Article>>

    /** Active, non-archived articles with their stock (sum of all movements, all locations). */
    @Query(
        """
        SELECT article.*, COALESCE(SUM(stock_movement.quantity), 0) AS stock
        FROM article
        LEFT JOIN stock_movement ON stock_movement.article_id = article.id
        WHERE article.deleted_at IS NULL AND article.archived = 0
        GROUP BY article.id
        ORDER BY article.name
        """,
    )
    abstract fun observeStock(): Flow<List<ArticleStock>>

    /**
     * Active, non-archived articles with their stock at [locationId] and all its descendants.
     * Only articles with at least one movement there are listed (their stock can be 0).
     * Descendants are included even if soft-deleted, so no stock disappears from the subtree.
     * UNION (not UNION ALL) stops on a parent_id cycle.
     */
    @Query(
        """
        WITH RECURSIVE subtree(id) AS (
            SELECT id FROM location WHERE id = :locationId
            UNION
            SELECT location.id FROM location JOIN subtree ON location.parent_id = subtree.id
        )
        SELECT article.*, SUM(stock_movement.quantity) AS stock
        FROM article
        JOIN stock_movement ON stock_movement.article_id = article.id
        WHERE stock_movement.location_id IN (SELECT id FROM subtree)
            AND article.deleted_at IS NULL AND article.archived = 0
        GROUP BY article.id
        ORDER BY article.name
        """,
    )
    abstract fun observeStockAt(locationId: String): Flow<List<ArticleStock>>

    /** Soft delete and free the NFC tag. Returns false if no active article has this id. */
    @Transaction
    open suspend fun softDelete(id: String, now: Long): Boolean {
        val article = getById(id) ?: return false
        update(article.softDeleted(now))
        return true
    }
}
