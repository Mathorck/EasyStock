package ch.mathorck.easystock.services.attributes

import ch.mathorck.easystock.enums.attributes.MeasureFamily

/** A quantity in the smallest unit of its [family]: equal quantities are equal ("50 m" == "5000 cm"). */
data class MeasureMagnitude(val family: MeasureFamily, val value: ExactDecimal)
