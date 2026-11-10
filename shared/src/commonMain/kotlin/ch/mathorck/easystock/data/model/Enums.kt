package ch.mathorck.easystock.data.model

// Enum-like columns are stored as lowercase strings (see docs/schema.dbml).

enum class AttributeDataType { TEXT, NUMBER, BOOL, DATE, ENUM }

enum class Tracking { QUANTITY, SERIAL }

enum class ItemStatus { AVAILABLE, LENT, REPAIR, LOST }

enum class MovementReason { PURCHASE, CONSUMPTION, INVENTORY, TRANSFER, LOAN, RETURN, LOSS }

/** Value stored in the database. */
val Enum<*>.dbValue: String get() = name.lowercase()

/** Parses a stored value. Throws on unknown values instead of guessing. */
inline fun <reified E : Enum<E>> enumFromDb(value: String): E =
    enumValues<E>().firstOrNull { it.dbValue == value }
        ?: throw IllegalArgumentException("Unknown ${E::class.simpleName} value: '$value'")
