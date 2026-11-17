package ch.mathorck.easystock.services.attributes

/** Why a template is not valid. [detail] is the unknown key or filter. */
data class NameTemplateError(val kind: Kind, val detail: String? = null) {
    enum class Kind {
        /** Unclosed "{", single "}", or a placeholder without key ("{}", "{|upper}"). */
        SYNTAX,
        /** No attribute of the category has this key (and it is not [CATEGORY_TEMPLATE_KEY]). */
        UNKNOWN_KEY,
        UNKNOWN_FILTER,
        TOO_LONG,
    }
}
