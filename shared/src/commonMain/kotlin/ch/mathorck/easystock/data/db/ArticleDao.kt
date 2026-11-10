package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.data.model.Article
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

    /** Soft delete and free the NFC tag. Returns false if no active article has this id. */
    @Transaction
    open suspend fun softDelete(id: String, now: Long): Boolean {
        val article = getById(id) ?: return false
        update(article.softDeleted(now))
        return true
    }
}
