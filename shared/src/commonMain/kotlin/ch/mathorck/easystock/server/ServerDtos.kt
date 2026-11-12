package ch.mathorck.easystock.server

import ch.mathorck.easystock.auth.Role
import kotlinx.serialization.Serializable

// JSON bodies of the server API (base path /api/v1, camelCase names, ISO 8601 UTC times as strings).
// Same shapes as the server's Models (server/src/EasyStock.Server/Models, server/openapi/v1.json).
// Every response field the app can live without has a default, so a newer server with more or fewer
// fields is still readable.

/** `GET /server-info` (public, no token, no version header needed). */
@Serializable
data class ServerInfo(
    val serverName: String = "",
    val serverVersion: String = "",
    val apiVersion: Int = 0,
    val minAppVersion: String = "",
    val supportedSchemaVersions: List<Int> = emptyList(),
    val setupDone: Boolean = false,
    val hasData: Boolean = false,
    val guestSignupEnabled: Boolean = false,
    /** The server's data set (changes when an Admin replaces the data): see [ch.mathorck.easystock.sync.DatasetEpochStore]. */
    val datasetEpoch: String = "",
)

/** User part of a token answer. */
@Serializable
data class SessionUser(
    val id: String,
    val displayName: String = "",
    val role: String = "",
    val kind: String = "",
    val shortCode: String = "",
)

/** Answer of setup, login, refresh, invite redeem, reset and promotion. */
@Serializable
data class TokenAnswer(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String? = null,
    val user: SessionUser? = null,
) {
    // Tokens must not end up in logs
    override fun toString(): String = "TokenAnswer(user=${user?.id})"
}

/** `POST /auth/refresh`. */
@Serializable
data class RefreshRequest(val refreshToken: String) {
    override fun toString(): String = "RefreshRequest"
}

/** `users.kind` on the server. */
object UserKind {
    const val FULL = "full"
    const val GUEST = "guest"
}

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
