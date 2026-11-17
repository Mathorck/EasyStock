package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ch.mathorck.easystock.data.model.ArticleFlag
import kotlinx.coroutines.flow.Flow

/** Longest flag note, in characters. The server uses the same limit. */
const val MAX_FLAG_NOTE_LENGTH = 1000

/**
 * Flags raised on articles (`article_flag`, synced). A flag is open while `resolved_at` and `deleted_at`
 * are null. Never hard-deleted. `created_by` is set by the server: no query here writes it.
 */
@Dao
abstract class ArticleFlagDao {
    @Insert
    protected abstract suspend fun insertRow(flag: ArticleFlag)

    /** Raises a flag. The phone sends no author: the server sets `created_by`. The note is at most [MAX_FLAG_NOTE_LENGTH]. */
    suspend fun insert(flag: ArticleFlag) {
        require(flag.note.length <= MAX_FLAG_NOTE_LENGTH) { "Flag note longer than $MAX_FLAG_NOTE_LENGTH characters" }
        insertRow(flag)
    }

    /** Resolves an open flag. Returns how many rows changed (0: unknown, already resolved or deleted). */
    @Query("UPDATE article_flag SET resolved_at = :now, updated_at = :now WHERE id = :id AND resolved_at IS NULL AND deleted_at IS NULL")
    abstract suspend fun resolve(id: String, now: Long): Int

    /** Soft delete. Returns how many rows changed (0: unknown or already deleted). */
    @Query("UPDATE article_flag SET deleted_at = :now, updated_at = :now WHERE id = :id AND deleted_at IS NULL")
    abstract suspend fun softDelete(id: String, now: Long): Int

    /** Open flags of an article, oldest first. */
    @Query(
        """SELECT * FROM article_flag WHERE article_id = :articleId AND resolved_at IS NULL AND deleted_at IS NULL
        ORDER BY created_at, id""",
    )
    abstract fun observeOpen(articleId: String): Flow<List<ArticleFlag>>

    /** Open flags of every article, oldest first. */
    @Query("SELECT * FROM article_flag WHERE resolved_at IS NULL AND deleted_at IS NULL ORDER BY created_at, id")
    abstract fun observeAllOpen(): Flow<List<ArticleFlag>>
}
