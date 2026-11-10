package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Separator between attribute values in the one-line summary. */
const val ATTRIBUTE_SUMMARY_SEPARATOR = " · "

/**
 * One attribute value of an article, ready to display and to search.
 * [raw] is the value without unit ("50"), [text] is what the row shows ("50 m").
 */
data class AttributeText(val raw: String, val text: String)

/** The summary line of an article: its values joined by [ATTRIBUTE_SUMMARY_SEPARATOR]. */
fun List<AttributeText>.summaryLine(): String = joinToString(ATTRIBUTE_SUMMARY_SEPARATOR) { it.text }

/** True when [query] is part of a value, raw or with its unit, ignoring case. */
fun List<AttributeText>.matches(query: String): Boolean =
    any { it.raw.contains(query, ignoreCase = true) || it.text.contains(query, ignoreCase = true) }

/** A number as stored without trailing zeros: "10.50" -> "10.5", "50.0" -> "50", "50" -> "50". */
fun formatAttributeNumber(text: String): String {
    val trimmed = text.trim()
    return if ('.' in trimmed) trimmed.trimEnd('0').trimEnd('.').ifEmpty { "0" } else trimmed
}

/**
 * Display text of one stored [value], or null when there is nothing to show
 * (unset, blank, false bool, wrong JSON type, invalid date).
 * Number -> "10.5 mm" (unit appended), bool -> its label when true, date -> ISO "yyyy-MM-dd",
 * text and enum -> the string as is.
 */
fun formatAttributeValue(definition: AttributeDefinition, value: JsonElement?): AttributeText? {
    val primitive = value as? JsonPrimitive ?: return null
    return when (definition.dataType) {
        AttributeDataType.TEXT, AttributeDataType.ENUM -> {
            if (!primitive.isString) return null
            primitive.content.trim().takeIf { it.isNotEmpty() }?.let { AttributeText(it, it) }
        }
        AttributeDataType.NUMBER -> {
            if (primitive.isString || primitive.content.isBlank()) return null
            val number = formatAttributeNumber(primitive.content)
            val unit = definition.unit?.trim().orEmpty()
            AttributeText(number, if (unit.isEmpty()) number else "$number $unit")
        }
        AttributeDataType.BOOL ->
            if (attributeBool(primitive)) AttributeText(definition.label, definition.label) else null
        AttributeDataType.DATE -> attributeDate(primitive)?.toString()?.let { AttributeText(it, it) }
    }
}

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
    fun format(article: Article): List<AttributeText> {
        val values = article.attributes ?: return emptyList()
        val order = article.categoryId?.let { sortOrders[it] }.orEmpty()
        return values.keys.mapNotNull { byKey[it] }
            .sortedWith(
                compareBy<AttributeDefinition> { order[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.id },
            )
            .mapNotNull { formatAttributeValue(it, values[it.key]) }
    }
}
