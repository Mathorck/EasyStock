package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Query
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import kotlinx.coroutines.flow.Flow

/**
 * Read-only rows for the past-date rule ([ch.mathorck.easystock.viewmodels.pastDates]): live, not archived articles
 * and items. Rows without values are left out in SQL, except an article that has an item with values: its item
 * needs it to be flagged. The definitions come from [AttributeDao.observeDefinitions].
 */
@Dao
abstract class DateAlertDao {
    @Query(
        """SELECT * FROM article WHERE deleted_at IS NULL AND archived = 0 AND (attributes IS NOT NULL OR id IN
        (SELECT article_id FROM item WHERE deleted_at IS NULL AND archived = 0 AND attributes IS NOT NULL))""",
    )
    abstract fun observeArticles(): Flow<List<Article>>

    @Query(
        """SELECT * FROM item WHERE deleted_at IS NULL AND archived = 0 AND attributes IS NOT NULL AND article_id IN
        (SELECT id FROM article WHERE deleted_at IS NULL AND archived = 0)""",
    )
    abstract fun observeItems(): Flow<List<Item>>

    /** The article with this id, as a list of zero or one row (deleted and archived ones are not returned). */
    @Query("SELECT * FROM article WHERE id = :articleId AND deleted_at IS NULL AND archived = 0")
    abstract fun observeArticle(articleId: String): Flow<List<Article>>

    @Query("SELECT * FROM item WHERE article_id = :articleId AND deleted_at IS NULL AND archived = 0 AND attributes IS NOT NULL")
    abstract fun observeItemsOf(articleId: String): Flow<List<Item>>
}
