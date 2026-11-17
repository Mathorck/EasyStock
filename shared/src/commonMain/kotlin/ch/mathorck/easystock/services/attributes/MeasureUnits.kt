package ch.mathorck.easystock.services.attributes

import ch.mathorck.easystock.enums.attributes.MeasureFamily

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
