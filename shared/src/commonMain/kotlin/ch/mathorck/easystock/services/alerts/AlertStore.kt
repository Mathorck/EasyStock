package ch.mathorck.easystock.services.alerts

import ch.mathorck.easystock.data.model.alerts.LowStockAlert
import ch.mathorck.easystock.data.model.article.ArticleStock

/** The alert rows the notifier reads and marks (data/db/AlertDao). */
interface AlertStore {
    /** `sync_state.role`, null in standalone mode. */
    suspend fun storedRole(): String?

    /** Alerts not yet notified, oldest first. */
    suspend fun pending(): List<LowStockAlert>

    /** The articles [ids] with their total stock, deleted and archived ones included. */
    suspend fun articleStock(ids: Collection<String>): List<ArticleStock>

    suspend fun markNotified(ids: Collection<String>)
}
