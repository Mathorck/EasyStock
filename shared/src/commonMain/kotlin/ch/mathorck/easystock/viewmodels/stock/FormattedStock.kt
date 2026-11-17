package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.data.model.article.ArticleStock
import ch.mathorck.easystock.services.attributes.AttributeText

/**
 * The articles of the selected location (or all, [locationId] null) with their formatted attributes.
 * Formatted once per database change, not on every search keystroke.
 */
internal data class FormattedStock(
    val locationId: String?,
    val items: List<ArticleStock>,
    val attributes: Map<String, List<AttributeText>>,
    val attributeLines: Map<String, String>,
)
