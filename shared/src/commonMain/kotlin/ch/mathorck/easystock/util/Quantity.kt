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
