package ch.mathorck.easystock.services.attributes

import ch.mathorck.easystock.composables.attributes.formatAttributeValue
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.CategoryAttribute
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import kotlinx.serialization.json.JsonObject

/**
 * Active attribute definitions and category links, loaded once, to format the attributes of any article.
 * Definitions and links must already exclude soft-deleted rows.
 */
class AttributeCatalog(definitions: List<AttributeDefinition>, links: List<CategoryAttribute>) {
    private val byKey: Map<String, AttributeDefinition> = definitions.associateBy { it.key }
    private val sortOrders: Map<String, Map<String, Int>> = links.groupBy { it.categoryId }
        .mapValues { (_, categoryLinks) -> categoryLinks.associate { it.attributeId to it.sortOrder } }

    /**
     * The values of [article], skipping empty ones. Attributes linked to the article's category come first,
     * by sort order, the others after, by label (ignoring case). Keys without active definition are ignored.
     */
    fun format(article: Article): List<AttributeText> = ordered(article.categoryId, article.attributes).map { it.second }

    /** Same values and order as [format], with the label of each attribute. A true bool shows "✓". */
    fun rows(categoryId: String?, values: JsonObject?): List<AttributeRow> = ordered(categoryId, values).map { (definition, text) ->
        AttributeRow(definition.key, definition.label, if (definition.dataType == AttributeDataType.BOOL) "✓" else text.text)
    }

    /**
     * The values a serial unit sets itself: those of [unit] that differ from the article's [inherited] ones.
     * A unit value equal to the article one is not repeated.
     */
    fun unitRows(categoryId: String?, inherited: JsonObject?, unit: JsonObject?): List<AttributeRow> {
        val own = rows(categoryId, unit)
        if (own.isEmpty()) return own
        val base = rows(categoryId, inherited).associate { it.key to it.value }
        return own.filter { base[it.key] != it.value }
    }

    private fun ordered(categoryId: String?, values: JsonObject?): List<Pair<AttributeDefinition, AttributeText>> {
        values ?: return emptyList()
        val order = categoryId?.let { sortOrders[it] }.orEmpty()
        return values.keys.mapNotNull { byKey[it] }
            .sortedWith(
                compareBy<AttributeDefinition> { order[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.id },
            )
            .mapNotNull { definition -> formatAttributeValue(definition, values[definition.key])?.let { definition to it } }
    }
}
