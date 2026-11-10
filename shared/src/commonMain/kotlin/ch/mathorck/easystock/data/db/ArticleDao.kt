package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import kotlinx.coroutines.flow.Flow

/** Result of [ArticleDao.linkTag]. */
sealed interface TagLinkResult {
    /** The tag is now on the article (also when it already was). */
    data object Linked : TagLinkResult

    /** Another active article holds the tag: nothing changed. Ask the user, then call again with its id. */
    data class LinkedElsewhere(val owner: Article) : TagLinkResult

    /** The article is deleted (or never existed): nothing changed. */
    data object ArticleGone : TagLinkResult
}

/** The tag is already linked to an active article. */
class TagAlreadyLinkedException(val tagId: String) : IllegalStateException("Tag already linked: $tagId")

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

    /** Active, non-archived articles without an NFC tag, the choice when linking a new tag. */
    @Query("SELECT * FROM article WHERE deleted_at IS NULL AND archived = 0 AND barcode IS NULL ORDER BY name COLLATE NOCASE")
    abstract fun observeUntagged(): Flow<List<Article>>

    /** Any row holding the tag, soft-deleted ones included (the unique index covers them, e.g. after a backup merge). */
    @Query("SELECT * FROM article WHERE barcode = :tagId LIMIT 1")
    protected abstract suspend fun findTagHolder(tagId: String): Article?

    @Query("UPDATE article SET barcode = :tagId, updated_at = :now WHERE id = :id")
    protected abstract suspend fun setBarcodeRow(id: String, tagId: String?, now: Long)

    @Query("UPDATE article SET barcode = NULL, updated_at = :now WHERE id = :id AND deleted_at IS NULL AND barcode IS NOT NULL")
    protected abstract suspend fun clearBarcodeRow(id: String, now: Long): Int

    /**
     * Links the NFC tag [tagId] to the active article [articleId], in one transaction.
     * If another active article holds the tag, nothing changes and [TagLinkResult.LinkedElsewhere] is returned,
     * unless it is [confirmedOwnerId] (the user agreed to move the tag from it): its tag is cleared first.
     * A soft-deleted row still holding the tag is freed without asking. Every changed row gets `updated_at` = [now].
     */
    @Transaction
    open suspend fun linkTag(articleId: String, tagId: String, confirmedOwnerId: String?, now: Long): TagLinkResult {
        val article = getById(articleId) ?: return TagLinkResult.ArticleGone
        val holder = findTagHolder(tagId)
        if (holder != null && holder.id != articleId) {
            if (holder.deletedAt == null && holder.id != confirmedOwnerId) return TagLinkResult.LinkedElsewhere(holder)
            // Cleared first: the unique index allows one row per tag
            setBarcodeRow(holder.id, null, now)
        }
        if (article.barcode != tagId) setBarcodeRow(articleId, tagId, now)
        return TagLinkResult.Linked
    }

    /** Removes the NFC tag of the active article. Returns false when there was none (or no such article). */
    @Transaction
    open suspend fun unlinkTag(articleId: String, now: Long): Boolean = clearBarcodeRow(articleId, now) > 0

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
