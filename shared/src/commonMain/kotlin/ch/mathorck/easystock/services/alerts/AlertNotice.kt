package ch.mathorck.easystock.services.alerts

/** What a low-stock notification shows: the article and its current stock on this phone. */
data class AlertNotice(
    val articleId: String,
    val articleName: String,
    /** Current stock, in thousandths of [unit]. */
    val stockMilli: Long,
    val unit: String,
)
