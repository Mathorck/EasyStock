package ch.mathorck.easystock.services.auth

import androidx.compose.runtime.compositionLocalOf
import ch.mathorck.easystock.enums.auth.Action
import ch.mathorck.easystock.enums.auth.Role

/**
 * The permissions of this phone: standalone (no `sync_state` row, everything allowed) or connected with [role].
 * [role] is null when connected with a role this app version doesn't know (Guest rights).
 */
data class AppPermissions(val connected: Boolean, val role: Role?) {
    fun can(action: Action): Boolean = Permissions.can(action, connected, role)

    companion object {
        /** No server: everything is allowed, the UI is the standalone one. */
        val Standalone = AppPermissions(connected = false, role = null)

        /** Connected to a server with [role]. */
        fun connectedAs(role: Role?) = AppPermissions(connected = true, role = role)

        /** From `sync_state.role`: null means no `sync_state` row, so standalone. */
        fun fromStoredRole(value: String?): AppPermissions =
            if (value == null) Standalone else connectedAs(Role.fromDb(value))
    }
}

/** Permissions of the current session, provided by MainComposable from `sync_state`. Standalone by default. */
val LocalPermissions = compositionLocalOf { AppPermissions.Standalone }
