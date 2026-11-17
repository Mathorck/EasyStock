package ch.mathorck.easystock.enums.auth

/**
 * Role of the user on a server (server mode only). Stored lowercase in `sync_state.role`.
 * Confirmed roles, see docs/server-mode/02-app.md, "Roles in the app".
 */
enum class Role(val dbValue: String) {
    /** Everything, including the administration screens. */
    ADMIN("admin"),

    /** Changes the stock and the catalogue, no administration. */
    EDITOR("editor"),

    /** Reads everything, movement history included, changes nothing. */
    VIEWER("viewer"),

    /** Printed QR code, no account: reads the stock only. */
    GUEST("guest"),
    ;

    companion object {
        /** Null for an unknown value (newer server): treated as the least rights by [Permissions]. */
        fun fromDb(value: String?): Role? = entries.firstOrNull { it.dbValue == value }
    }
}
