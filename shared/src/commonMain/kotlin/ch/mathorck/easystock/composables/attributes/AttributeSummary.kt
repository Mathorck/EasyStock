package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import ch.mathorck.easystock.enums.attributes.attributeBool
import ch.mathorck.easystock.enums.attributes.attributeDate
import ch.mathorck.easystock.enums.attributes.normalizeAttributeNumber
import ch.mathorck.easystock.services.attributes.AttributeText
import ch.mathorck.easystock.services.attributes.MeasureValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Separator between attribute values in the one-line summary. */
const val ATTRIBUTE_SUMMARY_SEPARATOR = " · "

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
