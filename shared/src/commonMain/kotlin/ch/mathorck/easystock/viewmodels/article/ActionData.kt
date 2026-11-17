package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.viewmodels.stock.SerialIndex
import kotlinx.serialization.json.JsonObject

internal data class ActionData(
    val stock: Map<String, Long>,
    val active: List<Location>,
    val all: List<Location>,
    val serials: SerialIndex,
    val items: List<Item>,
    val itemAttributes: List<CategoryAttributeWithDefinition> = emptyList(),
    val inheritedAttributes: JsonObject? = null,
)
