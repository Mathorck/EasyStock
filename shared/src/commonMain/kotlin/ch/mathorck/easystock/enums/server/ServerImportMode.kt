package ch.mathorck.easystock.enums.server

/** Backup import into the server. */
enum class ServerImportMode(val query: String) {
    /** Adds the backup to the server data, with the merge rules. */
    MERGE("merge"),

    /** Erases the server data and loads the backup. */
    REPLACE("replace"),
}
