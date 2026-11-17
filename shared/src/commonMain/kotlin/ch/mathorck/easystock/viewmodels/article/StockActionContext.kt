package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.viewmodels.stock.SerialIndex

/** Data the form is checked against. */
data class StockActionContext(
    /** Stock of the article per location id. */
    val stockByLocation: Map<String, Long>,
    val activeLocationIds: Set<String>,
    /** All serial numbers, deleted items included. */
    val serials: SerialIndex,
    val items: List<Item>,
    /** Attributes of the article category, for the unit attribute values. */
    val itemAttributes: List<CategoryAttributeWithDefinition> = emptyList(),
)
