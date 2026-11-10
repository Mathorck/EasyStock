package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull

// Values of custom attributes, stored as a JsonObject keyed by AttributeDefinition.key
// (article.attributes or item.attributes). Storage per data type:
//   text   -> JSON string
//   number -> JSON number, written as typed ("20", "1.5", "-3"), never through a Double
//   bool   -> JSON true / false
//   date   -> JSON string, ISO "yyyy-MM-dd"
//   enum   -> JSON string, one of AttributeDefinition.options
//   measure -> JSON object {"value": 50, "unit": "m"} (see Measure.kt); a plain number is read in the default unit
// An empty field has no key. Keys of other attributes (other category, deleted definition) are kept.

/** Max length of a text value. */
const val MAX_ATTRIBUTE_TEXT_LENGTH = 255

/** Max number of significant digits of a number value (safe for readers that use a Double). */
const val MAX_ATTRIBUTE_NUMBER_DIGITS = 15

/** Why an attribute value is not valid. */
enum class AttributeError {
    /** Required and empty. */
    REQUIRED,
    TOO_LONG,
    /** Not a number, or too many digits. */
    INVALID_NUMBER,
    /** Not an ISO date. */
    INVALID_DATE,
    /** Not one of the options (e.g. the option was removed since). */
    INVALID_OPTION,
    /** Wrong JSON type (e.g. a text attribute holding an object, a bool holding a string). */
    INVALID,
}

/** The value of [key], null when unset. */
fun JsonObject?.attributeValue(key: String): JsonElement? = this?.get(key)

/**
 * Copy of [this] with [key] set to [value], or removed when [value] is null.
 * Other keys are kept. Returns null when no key is left.
 */
fun JsonObject?.withAttributeValue(key: String, value: JsonElement?): JsonObject? {
    val map = this?.toMutableMap() ?: mutableMapOf()
    if (value == null) map.remove(key) else map[key] = value
    return if (map.isEmpty()) null else JsonObject(map)
}

/**
 * Parses a typed number: optional "-", digits, "." or "," as decimal separator ("1,5" -> 1.5).
 * Returns the JSON number, normalized ("01.50" -> 1.5, "2." -> 2), or null if it is not a number
 * or has more than [MAX_ATTRIBUTE_NUMBER_DIGITS] significant digits.
 */
@OptIn(ExperimentalSerializationApi::class)
fun parseAttributeNumber(text: String): JsonPrimitive? {
    val literal = normalizeAttributeNumber(text.trim()) ?: return null
    return JsonUnquotedLiteral(literal)
}

/** Normalized text of a typed number ("01,50" -> "1.5"), null when not a number. See [parseAttributeNumber]. */
internal fun normalizeAttributeNumber(text: String): String? {
    val negative = text.startsWith('-')
    val unsigned = if (negative) text.substring(1) else text
    val separator = unsigned.indexOfFirst { it == '.' || it == ',' }
    val whole = if (separator < 0) unsigned else unsigned.substring(0, separator)
    val fraction = if (separator < 0) "" else unsigned.substring(separator + 1)
    if (whole.isEmpty() && fraction.isEmpty()) return null
    if (!whole.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) return null
    val wholeDigits = whole.trimStart('0')
    val fractionDigits = fraction.trimEnd('0')
    if (wholeDigits.length + fractionDigits.length > MAX_ATTRIBUTE_NUMBER_DIGITS) return null
    val body = wholeDigits.ifEmpty { "0" } + if (fractionDigits.isEmpty()) "" else ".$fractionDigits"
    return if (negative && body != "0") "-$body" else body
}

/** Text shown in an input for [value]: the string, the number as stored, or a measure with its unit ("50 m"). Empty when unset. */
fun attributeInputText(value: JsonElement?): String = when (value) {
    is JsonPrimitive -> value.content
    is JsonObject -> listOf(measureInputText(value), ((value[MEASURE_UNIT_KEY] as? JsonPrimitive)?.content).orEmpty())
        .filter { it.isNotEmpty() }.joinToString(" ")
    else -> ""
}

/**
 * Value to store for typed [text] in a field of [dataType] (text, number, date or enum).
 * Blank -> null (key removed). A number that does not parse is kept as a string,
 * so the field keeps what was typed and [validateAttributes] reports it.
 */
fun attributeValueFromText(dataType: AttributeDataType, text: String): JsonElement? = when {
    text.isBlank() -> null
    dataType == AttributeDataType.NUMBER -> parseAttributeNumber(text) ?: JsonPrimitive(text)
    else -> JsonPrimitive(text)
}

/** True for a bool value, false when unset. */
fun attributeBool(value: JsonElement?): Boolean = (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: false

/** The stored date, null when unset or not an ISO date. */
fun attributeDate(value: JsonElement?): LocalDate? =
    (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Value to store for [date]: ISO string, null clears. */
fun attributeDateValue(date: LocalDate?): JsonElement? = date?.let { JsonPrimitive(it.toString()) }

/**
 * Checks [values] against [attributes]. Returns the errors keyed by [AttributeDefinition.key]; empty = valid.
 * Keys that are not in [attributes] are ignored. Required has no effect on bool attributes:
 * an unset switch means false. Callers must not save while errors exist.
 */
fun validateAttributes(attributes: List<CategoryAttributeWithDefinition>, values: JsonObject?): Map<String, AttributeError> =
    buildMap {
        attributes.forEach { attribute ->
            val definition = attribute.definition
            val value = values.attributeValue(definition.key)
            val error = if (value == null || (value is JsonPrimitive && value.isString && value.content.isBlank())) {
                if (attribute.link.required && definition.dataType != AttributeDataType.BOOL) AttributeError.REQUIRED else null
            } else {
                checkValue(definition, value)
            }
            error?.let { put(definition.key, it) }
        }
    }

private fun checkValue(definition: AttributeDefinition, value: JsonElement): AttributeError? {
    val primitive = value as? JsonPrimitive
    return when (definition.dataType) {
        AttributeDataType.TEXT -> when {
            primitive == null -> AttributeError.INVALID
            primitive.content.trim().length > MAX_ATTRIBUTE_TEXT_LENGTH -> AttributeError.TOO_LONG
            else -> null
        }
        AttributeDataType.NUMBER ->
            if (primitive != null && !primitive.isString && normalizeAttributeNumber(primitive.content) != null) null
            else AttributeError.INVALID_NUMBER
        AttributeDataType.BOOL ->
            if (primitive != null && !primitive.isString && primitive.booleanOrNull != null) null
            else AttributeError.INVALID
        AttributeDataType.DATE -> if (attributeDate(value) != null) null else AttributeError.INVALID_DATE
        AttributeDataType.ENUM ->
            if (primitive != null && primitive.isString && primitive.content in definition.options.orEmpty()) null
            else AttributeError.INVALID_OPTION
        AttributeDataType.MEASURE -> checkMeasure(definition, value)
    }
}
