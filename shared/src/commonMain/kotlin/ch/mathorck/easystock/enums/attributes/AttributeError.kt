package ch.mathorck.easystock.enums.attributes

/** Why an attribute value is not valid. */
enum class AttributeError {
    /** Required and empty. */
    REQUIRED,
    TOO_LONG,
    /** Not a number, or too many digits. */
    INVALID_NUMBER,
    /** Not an ISO date. */
    INVALID_DATE,
    /** Not one of the options (e.g. the option was removed since). */
    INVALID_OPTION,
    /** Wrong JSON type (e.g. a text attribute holding an object, a bool holding a string). */
    INVALID,
}
