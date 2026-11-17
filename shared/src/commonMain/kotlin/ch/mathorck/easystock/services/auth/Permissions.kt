package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.enums.auth.Action
import ch.mathorck.easystock.enums.auth.Role

/**
 * What each role can do, matching the server rules (docs/server-mode/02-app.md, "Roles in the app").
 * Standalone mode: everything is allowed, nothing changes for standalone users.
 * The UI reads it through [AppPermissions] and [LocalPermissions].
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

    private val FLAG = setOf(Action.FLAG_ARTICLE)

    private val byRole: Map<Role, Set<Action>> = mapOf(
        Role.ADMIN to Action.entries.toSet(),
        Role.EDITOR to READ + FLAG + WRITE,
        Role.VIEWER to READ + FLAG,
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
