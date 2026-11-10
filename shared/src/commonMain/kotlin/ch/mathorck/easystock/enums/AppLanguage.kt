package ch.mathorck.easystock.enums

/** In-app language choice. [tag] is the BCP 47 language tag, null follows the system. */
enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    French("fr"),
    German("de");

    companion object {
        /** Maps a locale tag (e.g. "fr-CH") to a language, System when empty or unsupported. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            if (language.isNullOrEmpty()) return System
            return entries.firstOrNull { it.tag == language } ?: System
        }
    }
}
