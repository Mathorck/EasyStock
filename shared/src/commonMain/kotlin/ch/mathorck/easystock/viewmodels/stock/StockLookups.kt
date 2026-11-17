package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.PastDate
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.viewmodels.alerts.pastDates

internal data class StockLookups(
    val locations: List<StockLocationNode>,
    val categories: List<Category>,
    val definitions: List<AttributeDefinition>,
    val flagged: Set<String>,
    val pastDates: Map<String, List<PastDate>>,
)
