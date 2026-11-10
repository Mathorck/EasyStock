package ch.mathorck.easystock.util

import kotlin.math.absoluteValue

/**
 * Formats a quantity stored in thousandths: 1500 -> "1.5", -250 -> "-0.25", 3000 -> "3".
 * Uses "." as decimal separator for now (no locale formatting in common code).
 */
fun formatQuantity(milli: Long): String {
    val sign = if (milli < 0) "-" else ""
    val abs = milli.absoluteValue
    val whole = abs / 1000
    val fraction = abs % 1000
    if (fraction == 0L) return "$sign$whole"
    return "$sign$whole." + fraction.toString().padStart(3, '0').trimEnd('0')
}

/** Largest quantity the schema can hold: decimal(12,3), so 999 999 999.999. */
const val MAX_QUANTITY_MILLI: Long = 999_999_999_999

/**
 * Parses a typed quantity into thousandths: "1.5" or "1,5" -> 1500, "0.001" -> 1, ".5" -> 500, "2." -> 2000.
 * Accepts ASCII digits and at most one "." or "," separator, with up to 3 decimals.
 * Returns null for anything else (sign, exponent, thousands separator, more than 3 decimals,
 * more than [MAX_QUANTITY_MILLI]). Never rounds. Zero is accepted, the caller checks if it must be positive.
 */
fun parseQuantity(text: String): Long? {
    val trimmed = text.trim()
    val separator = trimmed.indexOfFirst { it == '.' || it == ',' }
    val wholePart = if (separator < 0) trimmed else trimmed.substring(0, separator)
    val fractionPart = if (separator < 0) "" else trimmed.substring(separator + 1)
    if (wholePart.isEmpty() && fractionPart.isEmpty()) return null
    if (!wholePart.isAsciiDigits() || !fractionPart.isAsciiDigits()) return null
    if (fractionPart.length > 3) return null
    // At most 9 significant whole digits (decimal(12,3)), checked before any multiplication
    val significantWhole = wholePart.trimStart('0')
    if (significantWhole.length > 9) return null
    val whole = significantWhole.ifEmpty { "0" }.toLongOrNull() ?: return null
    val fraction = fractionPart.padEnd(3, '0').toLong()
    return whole * 1000 + fraction
}

private fun String.isAsciiDigits(): Boolean = all { it in '0'..'9' }
