package ch.mathorck.easystock.server

import ch.mathorck.easystock.auth.Role
import kotlinx.serialization.Serializable

// JSON bodies of the /admin endpoints. PROVISIONAL, see ServerDtos.kt: derived from the specs, not from
// server code. Lists are plain JSON arrays.

/** A pending promotion of a Guest: they must choose a username and password to get [role]. */
@Serializable
data class PendingPromotion(
    val role: String,
    val expiresAt: String? = null,
)

/** A device session of a user (`device_session`). */
@Serializable
data class AdminDevice(
    val id: String,
    val userId: String? = null,
    val deviceName: String = "",
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val revokedAt: String? = null,
) {
    val revoked: Boolean get() = revokedAt != null
}

/** A row of `GET /admin/users`. */
@Serializable
data class AdminUser(
    val id: String,
    val displayName: String = "",
    /** Null for a Guest (no username). */
    val userName: String? = null,
    val role: String = "",
    /** [UserKind]. */
    val kind: String = UserKind.FULL,
    val shortCode: String? = null,
    val createdAt: String? = null,
    /** Last request of any of the user's devices: the "last sync" of the list. */
    val lastSeenAt: String? = null,
    val disabledAt: String? = null,
    val devices: List<AdminDevice> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
) {
    val isGuest: Boolean get() = kind == UserKind.GUEST
    val disabled: Boolean get() = disabledAt != null
    val appRole: Role? get() = WireRole.parse(role)
}

/** `PATCH /admin/users/{id}`: only the fields that are set are sent. */
@Serializable
data class UpdateUserRequest(
    val role: String? = null,
    val displayName: String? = null,
    val disabled: Boolean? = null,
)

/** `POST /admin/users/{id}/promote`. [shortCode] is the code the Admin typed, as shown by the Guest. */
@Serializable
data class PromoteRequest(val role: String, val shortCode: String)

/**
 * A single-use token for a QR code (`POST /admin/users/{id}/reset`). The app builds the QR code itself
 * from [token] with the `sync_state` address ([JoinCode.build]), so it always has the app's format;
 * [qr] (the content built by the server) is used only when no token is sent.
 */
@Serializable
data class QrGrant(
    val token: String? = null,
    val qr: String? = null,
    val expiresAt: String? = null,
) {
    override fun toString(): String = "QrGrant(expiresAt=$expiresAt)"

    /**
     * The QR code text for [kind] on [connection]: built by the app from [token], else the server's [qr]
     * if it is a valid code of that kind. Null when there is neither.
     */
    fun qrText(connection: ServerConnection, kind: JoinKind): String? {
        token?.takeIf { it.isNotBlank() }?.let { token ->
            runCatching { JoinCode.build(connection.serverUrl, kind, token, connection.fingerprint) }
                .getOrNull()?.let { return it.toQrText() }
        }
        val fromServer = qr?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JoinCode.parse(fromServer) }.getOrNull()?.takeIf { it.kind == kind }?.toQrText()
    }
}

/** `POST /admin/invites`. */
@Serializable
data class CreateInviteRequest(val role: String)

/** An open invite. [token] and [qr] are only sent when it is created: the server keeps a hash. */
@Serializable
data class Invite(
    val id: String,
    val role: String = "",
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val token: String? = null,
    val qr: String? = null,
) {
    val grant: QrGrant get() = QrGrant(token, qr, expiresAt)

    override fun toString(): String = "Invite(id=$id, role=$role, expiresAt=$expiresAt)"
}

/**
 * The printed QR code (`/admin/public-invite`). [token] and [qr] may be missing when the server only keeps
 * a hash: then the PDF can only be made right after [regenerate][AdminApi.regeneratePublicInvite].
 */
@Serializable
data class PublicInvite(
    val enabled: Boolean = false,
    /** Most Guest accounts at a time, null = no limit. */
    val guestLimit: Int? = null,
    /** Guest accounts now, when the server says it. */
    val guestCount: Int? = null,
    val createdAt: String? = null,
    val token: String? = null,
    val qr: String? = null,
) {
    val grant: QrGrant get() = QrGrant(token, qr)

    override fun toString(): String = "PublicInvite(enabled=$enabled, guestLimit=$guestLimit, guestCount=$guestCount)"
}

/**
 * `GET`/`PUT /admin/settings`. A field the server doesn't send is null: the screen hides it and the
 * app doesn't send it back (the server refuses unknown keys).
 */
@Serializable
data class AdminSettings(
    val serverName: String? = null,
    /** Days after which the author of a movement is removed, null = keep. */
    val movementAuthorRetentionDays: Int? = null,
    /** Roles that get low-stock alerts by default (wire names). Null when the server has no such setting. */
    val alertRoles: List<String>? = null,
)

/** Backup import into the server. */
enum class ServerImportMode(val query: String) {
    /** Adds the backup to the server data, with the merge rules. */
    MERGE("merge"),

    /** Erases the server data and loads the backup. */
    REPLACE("replace"),
}
