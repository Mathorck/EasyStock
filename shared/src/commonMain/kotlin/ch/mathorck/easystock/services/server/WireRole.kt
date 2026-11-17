package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.enums.auth.Role

/**
 * Role names on the wire. The app calls the second role "Editor" (`editor` in `sync_state`, confirmed in
 * docs/server-mode/02-app.md), the server spec calls it `member`. Every role sent to or read from the
 * server goes through here, so the decision stays in one place: the app sends [EDITOR_WIRE] and reads
 * both names.
 */
object WireRole {
    /** Name sent for [Role.EDITOR]. PROVISIONAL: the server spec says `member`, see the mismatch note above. */
    const val EDITOR_WIRE = "member"

    fun of(role: Role): String = if (role == Role.EDITOR) EDITOR_WIRE else role.dbValue

    /** Null for a role this app doesn't know (newer server). */
    fun parse(value: String?): Role? = when (value) {
        "member", "editor" -> Role.EDITOR
        else -> Role.fromDb(value)
    }

    /** Roles an Admin can give to a full account or an invite (never Guest: a Guest comes from the printed code). */
    val assignable: List<Role> = listOf(Role.ADMIN, Role.EDITOR, Role.VIEWER)
}
