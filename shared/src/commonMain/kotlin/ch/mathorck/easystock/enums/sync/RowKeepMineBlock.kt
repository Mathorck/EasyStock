package ch.mathorck.easystock.enums.sync

/** Why "Keep mine" is not offered for a row conflict. */
enum class RowKeepMineBlock {
    /** The role can't send changes any more. */
    ROLE,

    /** The row is no longer on this phone. */
    ROW_GONE,

    /** Another row now holds a unique value of the phone's version (name, SKU, NFC tag, serial number). */
    VALUE_TAKEN,

    /** The phone's version can't be written (unreadable, or a missing parent). */
    INVALID,
}
