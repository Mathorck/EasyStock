package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import ch.mathorck.easystock.enums.attributes.AttributeError
import ch.mathorck.easystock.enums.attributes.normalizeAttributeNumber
import ch.mathorck.easystock.services.attributes.MeasureUnit
import ch.mathorck.easystock.services.attributes.MeasureUnits
import ch.mathorck.easystock.services.attributes.MeasureValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

// Measure attributes (data_type = measure): a number with a unit chosen per value ("50 m", "9.8 mm").
// AttributeDefinition.unit holds the default unit, which also defines the family.
//
// Stored value: {"value": 50, "unit": "m"}. "value" is a JSON number written as typed (see parseAttributeNumber).
// A plain JSON number (value saved while the attribute was a number) is read in the default unit.
//
// Every unit of a family is a power of ten of the smallest one: values convert exactly, by moving the
// decimal point, never through a Double.

/** JSON keys of a measure value. */
const val MEASURE_VALUE_KEY = "value"

const val MEASURE_UNIT_KEY = "unit"

/** The default unit of a measure (or number) definition, null when its unit is not a known one. */
val AttributeDefinition.measureUnit: MeasureUnit? get() = MeasureUnits.find(unit)

/**
 * Reads a stored measure: {"value": n, "unit": "m"}, or a plain number in the default unit of [definition].
 * Null when unset or not valid (string value, unknown unit, unit of another family).
 */
fun readMeasure(definition: AttributeDefinition, value: JsonElement?): MeasureValue? {
    val default = definition.measureUnit
    return when (value) {
        is JsonObject -> {
            val number = (value[MEASURE_VALUE_KEY] as? JsonPrimitive)?.takeIf { !it.isString }?.content ?: return null
            val normalized = normalizeAttributeNumber(number) ?: return null
            val unitPrimitive = value[MEASURE_UNIT_KEY] as? JsonPrimitive
            val unit = if (unitPrimitive == null) default else MeasureUnits.find(unitPrimitive.takeIf { it.isString }?.content)
            if (unit == null || (default != null && unit.family != default.family)) return null
            MeasureValue(normalized, unit)
        }
        is JsonPrimitive -> {
            if (value.isString || default == null) return null
            normalizeAttributeNumber(value.content)?.let { MeasureValue(it, default) }
        }
        else -> null
    }
}

/**
 * Value to store for a measure field: typed [text] and the chosen [unit] symbol.
 * Blank -> null (key removed). A number that does not parse is kept as a string in "value",
 * so the field keeps what was typed and [validateAttributes] reports it.
 */
@OptIn(ExperimentalSerializationApi::class)
fun measureValueFromInput(text: String, unit: String): JsonElement? {
    if (text.isBlank()) return null
    val number = normalizeAttributeNumber(text.trim())
    return JsonObject(
        mapOf(
            MEASURE_VALUE_KEY to (number?.let { JsonUnquotedLiteral(it) } ?: JsonPrimitive(text)),
            MEASURE_UNIT_KEY to JsonPrimitive(unit),
        ),
    )
}

/** Text of the number of a stored measure for the input field (as stored, also when not valid). */
fun measureInputText(value: JsonElement?): String = when (value) {
    is JsonObject -> (value[MEASURE_VALUE_KEY] as? JsonPrimitive)?.content.orEmpty()
    is JsonPrimitive -> value.content
    else -> ""
}

/** Unit of a stored measure for the dropdown: its own when known, else [default]. */
fun measureInputUnit(value: JsonElement?, default: String): String =
    ((value as? JsonObject)?.get(MEASURE_UNIT_KEY) as? JsonPrimitive)?.content
        ?.let { MeasureUnits.find(it)?.symbol } ?: default

/** Error of a stored measure value, null when valid. */
internal fun checkMeasure(definition: AttributeDefinition, value: JsonElement): AttributeError? = when {
    readMeasure(definition, value) != null -> null
    value is JsonPrimitive && value.isString -> AttributeError.INVALID_NUMBER
    value is JsonObject && (value[MEASURE_VALUE_KEY] as? JsonPrimitive).let { it == null || it.isString || normalizeAttributeNumber(it.content) == null } ->
        AttributeError.INVALID_NUMBER
    else -> AttributeError.INVALID
}

/**
 * Parses a search query or typed text as a number with a known unit: "50m", "50 m", "0.5 km", "9,8 mm".
 * Null when it is not exactly a number followed by a unit.
 */
fun parseMeasureText(text: String): MeasureValue? {
    val match = MEASURE_TEXT.matchEntire(text.trim()) ?: return null
    val unit = MeasureUnits.find(match.groupValues[2]) ?: return null
    val number = normalizeAttributeNumber(match.groupValues[1]) ?: return null
    return MeasureValue(number, unit)
}

private val MEASURE_TEXT = Regex("""(-?[0-9]*[.,]?[0-9]*)\s*([A-Za-z]+)""")

/** True for a number attribute whose unit is a known one: it can become a measure, its values keep their meaning. */
fun AttributeDefinition.canBecomeMeasure(): Boolean = dataType == AttributeDataType.NUMBER && canBecomeMeasure(unit)

/** True when a number attribute with [unit] can become a measure: no unit yet, or a known one. */
fun canBecomeMeasure(unit: String?): Boolean = unit.isNullOrBlank() || MeasureUnits.find(unit) != null
