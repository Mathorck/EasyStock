package ch.mathorck.easystock.services.attributes

import ch.mathorck.easystock.enums.attributes.MeasureFamily

/** A unit: 1 [symbol] = 10^[exponent] of the smallest unit of its [family]. */
data class MeasureUnit(val symbol: String, val family: MeasureFamily, val exponent: Int)
