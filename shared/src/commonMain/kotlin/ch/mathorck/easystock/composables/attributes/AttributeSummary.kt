package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Separator between attribute values in the one-line summary. */
const val ATTRIBUTE_SUMMARY_SEPARATOR = " · "

/**
 * One attribute value of an article, ready to display and to search.
 * [raw] is the value without unit ("50"), [text] is what the row shows ("50 m").
 * [measure]: the quantity, for a measure or a number with a known unit, so a search in another unit finds it.
 */
data class AttributeText(val raw: String, val text: String, val measure: MeasureMagnitude? = null)

/** One attribute of an article for the detail view: its [label] and the formatted [value] ("50 m", "✓" for a bool). */
data class AttributeRow(val key: String, val label: String, val value: String)

/** The summary line of an article: its values joined by [ATTRIBUTE_SUMMARY_SEPARATOR]. */
fun List<AttributeText>.summaryLine(): String = joinToString(ATTRIBUTE_SUMMARY_SEPARATOR) { it.text }

/**
 * True when [query] is part of a value, raw or with its unit, ignoring case.
 * A query that is a number with a unit ("5000 cm", "50m") also matches an equal quantity in another unit
 * of the same family ("50 m").
 */
fun List<AttributeText>.matches(query: String): Boolean {
    val measure = parseMeasureText(query)?.magnitude
    return any {
        it.raw.contains(query, ignoreCase = true) || it.text.contains(query, ignoreCase = true) ||
            (measure != null && it.measure == measure)
    }
}

/** A number as stored without trailing zeros: "10.50" -> "10.5", "50.0" -> "50", "50" -> "50". */
fun formatAttributeNumber(text: String): String {
    val trimmed = text.trim()
    return if ('.' in trimmed) trimmed.trimEnd('0').trimEnd('.').ifEmpty { "0" } else trimmed
}

/**
 * Display text of one stored [value], or null when there is nothing to show
 * (unset, blank, false bool, wrong JSON type, invalid date).
 * Number -> "10.5 mm" (unit appended), measure -> "50 m" (as entered), bool -> its label when true,
 * date -> ISO "yyyy-MM-dd", text and enum -> the string as is.
 */
fun formatAttributeValue(definition: AttributeDefinition, value: JsonElement?): AttributeText? {
    if (definition.dataType == AttributeDataType.MEASURE) {
        val measure = readMeasure(definition, value) ?: return null
        return AttributeText(formatAttributeNumber(measure.number), measure.text, measure.magnitude)
    }
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
            // A known unit makes the number searchable in other units too
            val measure = definition.measureUnit?.let { normalizeAttributeNumber(number)?.let { n -> MeasureValue(n, it).magnitude } }
            AttributeText(number, if (unit.isEmpty()) number else "$number $unit", measure)
        }
        AttributeDataType.BOOL ->
            if (attributeBool(primitive)) AttributeText(definition.label, definition.label) else null
        AttributeDataType.DATE -> attributeDate(primitive)?.toString()?.let { AttributeText(it, it) }
        AttributeDataType.MEASURE -> null
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
