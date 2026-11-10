package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
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

/** Kind of quantity. Units of the same family convert to each other. [defaultUnit]: proposed for a new attribute. */
enum class MeasureFamily(val defaultUnit: String) {
    LENGTH("m"),
    MASS("kg"),
    VOLUME("l"),
    FORCE("kN"),
}

/** A unit: 1 [symbol] = 10^[exponent] of the smallest unit of its [family]. */
data class MeasureUnit(val symbol: String, val family: MeasureFamily, val exponent: Int)

object MeasureUnits {
    /** All units, by family, small to large. */
    val all: List<MeasureUnit> = listOf(
        MeasureUnit("mm", MeasureFamily.LENGTH, 0),
        MeasureUnit("cm", MeasureFamily.LENGTH, 1),
        MeasureUnit("m", MeasureFamily.LENGTH, 3),
        MeasureUnit("km", MeasureFamily.LENGTH, 6),
        MeasureUnit("mg", MeasureFamily.MASS, 0),
        MeasureUnit("g", MeasureFamily.MASS, 3),
        MeasureUnit("kg", MeasureFamily.MASS, 6),
        MeasureUnit("t", MeasureFamily.MASS, 9),
        MeasureUnit("ml", MeasureFamily.VOLUME, 0),
        MeasureUnit("cl", MeasureFamily.VOLUME, 1),
        MeasureUnit("dl", MeasureFamily.VOLUME, 2),
        MeasureUnit("l", MeasureFamily.VOLUME, 3),
        MeasureUnit("N", MeasureFamily.FORCE, 0),
        MeasureUnit("daN", MeasureFamily.FORCE, 1),
        MeasureUnit("kN", MeasureFamily.FORCE, 3),
    )

    /** The unit written [symbol], trimmed, ignoring case ("KN" -> kN). Null when unknown. */
    fun find(symbol: String?): MeasureUnit? {
        val s = symbol?.trim().orEmpty()
        if (s.isEmpty()) return null
        return all.firstOrNull { it.symbol.equals(s, ignoreCase = true) }
    }

    fun of(family: MeasureFamily): List<MeasureUnit> = all.filter { it.family == family }
}

/**
 * An exact decimal: [digits] × 10^[exponent], [digits] without leading or trailing zeros ("" for zero).
 * Two equal quantities have equal instances, so `==` compares values.
 */
data class ExactDecimal(val negative: Boolean, val digits: String, val exponent: Int) {
    val isZero: Boolean get() = digits.isEmpty()

    /** Same value × 10^[shift]. */
    fun shift(shift: Int): ExactDecimal = if (isZero) this else copy(exponent = exponent + shift)

    /** Plain notation, no exponent, no trailing zeros: "50", "0.0098", "-1.5". */
    fun toPlainString(): String {
        if (isZero) return "0"
        val sign = if (negative) "-" else ""
        val body = when {
            exponent >= 0 -> digits + "0".repeat(exponent)
            -exponent >= digits.length -> "0." + "0".repeat(-exponent - digits.length) + digits
            else -> digits.substring(0, digits.length + exponent) + "." + digits.substring(digits.length + exponent)
        }
        return sign + body
    }

    companion object {
        /** Parses a normalized number ("-1.5", "50", "0.02"), see [normalizeAttributeNumber]. */
        fun parse(normalized: String): ExactDecimal? {
            val negative = normalized.startsWith('-')
            val unsigned = normalized.removePrefix("-")
            val dot = unsigned.indexOf('.')
            val whole = if (dot < 0) unsigned else unsigned.substring(0, dot)
            val fraction = if (dot < 0) "" else unsigned.substring(dot + 1)
            if (!(whole + fraction).all { it in '0'..'9' } || (whole + fraction).isEmpty()) return null
            val all = (whole + fraction).trimStart('0')
            val trimmed = all.trimEnd('0')
            if (trimmed.isEmpty()) return ExactDecimal(false, "", 0)
            return ExactDecimal(negative, trimmed, all.length - trimmed.length - fraction.length)
        }
    }
}

/** A measure value as entered: [number] normalized ("9.8"), in [unit]. */
data class MeasureValue(val number: String, val unit: MeasureUnit) {
    /** "9.8 mm" */
    val text: String get() = "${formatAttributeNumber(number)} ${unit.symbol}"

    /** Value in the smallest unit of the family, for comparisons. */
    val magnitude: MeasureMagnitude?
        get() = ExactDecimal.parse(number)?.let { MeasureMagnitude(unit.family, it.shift(unit.exponent)) }

    /** The number in [target] ("50 m" in cm -> "5000"), null when [target] is another family. */
    fun numberIn(target: MeasureUnit): String? {
        if (target.family != unit.family) return null
        return ExactDecimal.parse(number)?.shift(unit.exponent - target.exponent)?.toPlainString()
    }
}

/** A quantity in the smallest unit of its [family]: equal quantities are equal ("50 m" == "5000 cm"). */
data class MeasureMagnitude(val family: MeasureFamily, val value: ExactDecimal)

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
