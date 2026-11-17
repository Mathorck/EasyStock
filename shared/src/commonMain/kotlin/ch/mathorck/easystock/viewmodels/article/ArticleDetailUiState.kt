package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.isLowAt
import ch.mathorck.easystock.data.model.attributes.PastDate
import ch.mathorck.easystock.services.attributes.AttributeRow
import ch.mathorck.easystock.viewmodels.alerts.pastDates
import ch.mathorck.easystock.viewmodels.location.LocationStockRow

data class ArticleDetailUiState(
    /** True until the database has answered. */
    val loading: Boolean = true,
    /** Null once loaded means the article is deleted (or never existed). */
    val article: Article? = null,
    val categoryName: String? = null,
    val totalMilli: Long = 0,
    val locations: List<LocationStockRow> = emptyList(),
    /** Movements and "info to fix" events, newest first. Only the newest movements, see [hasMoreHistory]. */
    val history: List<HistoryRow> = emptyList(),
    /** Older movements are not loaded yet: the screen offers to show more. */
    val hasMoreHistory: Boolean = false,
    /** Open "info to fix" flags from finished reviews. */
    val infoFlags: List<InfoFlagRow> = emptyList(),
    /** Open manual flags ("Report a problem"). */
    val problems: List<ManualFlagRow> = emptyList(),
    /** Attribute values of the article, empty ones skipped, in the category order. */
    val properties: List<AttributeRow> = emptyList(),
    /** Past dates of this article and its units (date attributes that flag when past). */
    val pastDates: List<PastDate> = emptyList(),
) {
    val isDeleted: Boolean get() = !loading && article == null

    /** Same rule as [ch.mathorck.easystock.data.model.article.ArticleStock.isLowStock]. */
    val isLowStock: Boolean get() = article?.isLowAt(totalMilli) ?: false
}
