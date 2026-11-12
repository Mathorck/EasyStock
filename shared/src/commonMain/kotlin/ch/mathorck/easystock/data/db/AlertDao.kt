package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Query
import ch.mathorck.easystock.alerts.AlertStore
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.LowStockAlert

/** Low-stock alerts received from the server (`low_stock_alert`), for the notifications. Empty in standalone mode. */
@Dao
abstract class AlertDao {
    /** `sync_state.role`, null in standalone mode. */
    @Query("SELECT role FROM sync_state LIMIT 1")
    abstract suspend fun storedRole(): String?

    @Query("SELECT * FROM low_stock_alert WHERE notified = 0 ORDER BY crossed_at")
    abstract suspend fun pending(): List<LowStockAlert>

    /** Articles [ids] with their total stock, deleted and archived ones included. At most 999 ids. */
    @Query(
        """
        SELECT article.*, COALESCE(SUM(stock_movement.quantity), 0) AS stock
        FROM article
        LEFT JOIN stock_movement ON stock_movement.article_id = article.id
        WHERE article.id IN (:ids)
        GROUP BY article.id
        """,
    )
    abstract suspend fun articleStock(ids: List<String>): List<ArticleStock>

    /** Never deleted: a later full pull would bring the alert back as new. At most 999 ids. */
    @Query("UPDATE low_stock_alert SET notified = 1 WHERE id IN (:ids)")
    abstract suspend fun markNotified(ids: List<String>)
}

private const val IN_CHUNK = 500

/** The [AlertStore] of the notifier, on the database. */
fun AlertDao.asAlertStore(): AlertStore {
    val dao = this
    return object : AlertStore {
        override suspend fun storedRole(): String? = dao.storedRole()

        override suspend fun pending(): List<LowStockAlert> = dao.pending()

        override suspend fun articleStock(ids: Collection<String>): List<ArticleStock> =
            ids.toList().chunked(IN_CHUNK).flatMap { dao.articleStock(it) }

        override suspend fun markNotified(ids: Collection<String>) =
            ids.toList().chunked(IN_CHUNK).forEach { dao.markNotified(it) }
    }
}
