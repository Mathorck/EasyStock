package ch.mathorck.easystock.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import ch.mathorck.easystock.data.model.ArticleFlag
import kotlinx.coroutines.flow.Flow

/** Longest flag note, in characters. The server uses the same limit. */
const val MAX_FLAG_NOTE_LENGTH = 1000

/** A flag with the display name of its author (null in standalone mode, or when the user is not in `server_user`). */
data class ArticleFlagWithAuthor(
    @Embedded val flag: ArticleFlag,
    @ColumnInfo(name = "author_name") val authorName: String? = null,
)

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

    /**
     * Flags of an article that are not deleted, resolved ones included (for the history), oldest first,
     * with the author's name.
     */
    @Query(
        """SELECT article_flag.*, server_user.display_name AS author_name FROM article_flag
        LEFT JOIN server_user ON server_user.user_id = article_flag.created_by
        WHERE article_flag.article_id = :articleId AND article_flag.deleted_at IS NULL
        ORDER BY article_flag.created_at, article_flag.id""",
    )
    abstract fun observeForArticle(articleId: String): Flow<List<ArticleFlagWithAuthor>>

    /**
     * Ids of the articles with an open flag of any source: a manual flag, or an "info to fix" line of a finished
     * review not fixed yet. One query for the whole Stock list, each id once.
     */
    @Query(
        """SELECT article_id FROM article_flag WHERE resolved_at IS NULL AND deleted_at IS NULL
        UNION
        SELECT l.article_id FROM stock_review_line l JOIN stock_review r ON r.id = l.review_id
        WHERE l.info_flagged = 1 AND l.info_fixed_at IS NULL AND l.deleted_at IS NULL
          AND r.status = 'finished' AND r.deleted_at IS NULL AND r.finished_at IS NOT NULL""",
    )
    abstract fun observeFlaggedArticleIds(): Flow<List<String>>
}
