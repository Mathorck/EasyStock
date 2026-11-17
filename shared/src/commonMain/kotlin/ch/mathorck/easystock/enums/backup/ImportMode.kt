package ch.mathorck.easystock.enums.backup

enum class ImportMode {
    /** Wipes this phone and restores the backup. */
    REPLACE,

    /** Adds the backup to this phone, newer rows win. */
    MERGE,
}
