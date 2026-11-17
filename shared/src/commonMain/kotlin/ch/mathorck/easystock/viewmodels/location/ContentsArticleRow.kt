package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.enums.article.Tracking
import ch.mathorck.easystock.services.attributes.AttributeText

/** One article at one location: its stock there, its attributes and, for serial articles, the units there. */
data class ContentsArticleRow(
    val article: Article,
    val locationId: String,
    val stockMilli: Long,
    val attributes: List<AttributeText>,
    val units: List<Item>,
) {
    val isSerial: Boolean get() = article.tracking == Tracking.SERIAL
}
