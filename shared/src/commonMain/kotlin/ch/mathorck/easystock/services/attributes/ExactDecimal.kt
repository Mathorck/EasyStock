package ch.mathorck.easystock.services.attributes

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
