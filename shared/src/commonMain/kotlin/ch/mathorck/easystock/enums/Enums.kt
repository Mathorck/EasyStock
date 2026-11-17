package ch.mathorck.easystock.enums

/** Value stored in the database. */
val Enum<*>.dbValue: String get() = name.lowercase()

/** Parses a stored value. Throws on unknown values instead of guessing. */
inline fun <reified E : Enum<E>> enumFromDb(value: String): E =
    enumValues<E>().firstOrNull { it.dbValue == value }
        ?: throw IllegalArgumentException("Unknown ${E::class.simpleName} value: '$value'")
