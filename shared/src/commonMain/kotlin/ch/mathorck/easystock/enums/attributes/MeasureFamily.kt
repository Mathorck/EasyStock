package ch.mathorck.easystock.enums.attributes

/** Kind of quantity. Units of the same family convert to each other. [defaultUnit]: proposed for a new attribute. */
enum class MeasureFamily(val defaultUnit: String) {
    LENGTH("m"),
    MASS("kg"),
    VOLUME("l"),
    FORCE("kN"),
}
