package ch.mathorck.easystock.enums.attributes

/** A filter after "|" in a placeholder. */
enum class NameTemplateFilter(val id: String) {
    /** Number without its unit ("10.5" instead of "10.5 mm"). Same as the value for other types. */
    RAW("raw"),
    UPPER("upper"),
    LOWER("lower"),
    /** First letter upper-cased. */
    CAPITALIZE("capitalize"),
    ;

    companion object {
        fun fromId(id: String): NameTemplateFilter? = entries.firstOrNull { it.id == id }
    }
}
