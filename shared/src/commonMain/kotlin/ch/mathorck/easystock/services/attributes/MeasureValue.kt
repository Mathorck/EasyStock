package ch.mathorck.easystock.services.attributes

import ch.mathorck.easystock.composables.attributes.formatAttributeNumber

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
