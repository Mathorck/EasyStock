package ch.mathorck.easystock.services.attributes

/**
 * One attribute value of an article, ready to display and to search.
 * [raw] is the value without unit ("50"), [text] is what the row shows ("50 m").
 * [measure]: the quantity, for a measure or a number with a known unit, so a search in another unit finds it.
 */
data class AttributeText(val raw: String, val text: String, val measure: MeasureMagnitude? = null)
