package ch.mathorck.easystock.auth

/**
 * Role of the user on a server (server mode only). Stored lowercase in `sync_state.role`.
 * The names are an assumption until the server spec is written (docs/server-mode/02-app.md, "Roles").
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

/** What a user can do in the app. The UI hides what is not allowed; the server checks everything again. */
enum class Action {
    /** Stock list, article and location details, search. */
    VIEW_STOCK,

    /** Movement history section of the article screen. */
    VIEW_HISTORY,

    /** Reviews screen and finished reviews. */
    VIEW_REVIEWS,

    /** Add, remove, transfer, lend, return stock. */
    STOCK_ACTION,

    /** Create and edit articles, units, locations, categories, attributes, name templates. */
    EDIT,

    /** Soft-delete articles, units, locations, categories, attributes. */
    DELETE,

    /** Link an NFC tag to an article or a location, or unlink it. */
    LINK_TAG,

    /** Start, count and finish a stock review. */
    RUN_REVIEW,

    /** Settings screens that change data: categories, attributes, locations, tags, backup import. */
    MANAGE_SETTINGS,

    /** Send local changes to the server (sync push). */
    PUSH_CHANGES,

    /** Low-stock alert notifications (a Viewer must turn them on, a Guest never gets them). */
    RECEIVE_ALERTS,

    /** Users, invites, printed QR code, server backup, server settings. */
    ADMINISTER,
}

/**
 * What each role can do, matching the server rules (to confirm with the server spec).
 * Standalone mode: everything is allowed, nothing changes for standalone users.
 * Not wired into the UI yet.
 */
object Permissions {
    private val READ = setOf(Action.VIEW_STOCK, Action.VIEW_HISTORY, Action.VIEW_REVIEWS, Action.RECEIVE_ALERTS)

    private val WRITE = setOf(
        Action.STOCK_ACTION,
        Action.EDIT,
        Action.DELETE,
        Action.LINK_TAG,
        Action.RUN_REVIEW,
        Action.MANAGE_SETTINGS,
        Action.PUSH_CHANGES,
    )

    private val byRole: Map<Role, Set<Action>> = mapOf(
        Role.ADMIN to Action.entries.toSet(),
        Role.EDITOR to READ + WRITE,
        Role.VIEWER to READ,
        Role.GUEST to setOf(Action.VIEW_STOCK),
    )

    /** What [role] can do on a server. An unknown role (null) gets the rights of a [Role.GUEST]. */
    fun allowed(role: Role?): Set<Action> = byRole.getValue(role ?: Role.GUEST)

    /**
     * The single entry point for the UI. [connected] is false in standalone mode (no `sync_state` row):
     * everything is allowed. Connected, [role] is the stored role, null when this app version doesn't know it.
     */
    fun can(action: Action, connected: Boolean, role: Role?): Boolean =
        !connected || action in allowed(role)
}
