package ch.mathorck.easystock.server

import ch.mathorck.easystock.auth.Role
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** The `/admin` calls (Admin role only). Every call throws [ApiException] on failure. */
interface AdminApi {
    suspend fun serverInfo(): ServerInfo

    suspend fun users(): List<AdminUser>

    suspend fun changeRole(userId: String, role: Role): AdminUser

    suspend fun setDisabled(userId: String, disabled: Boolean): AdminUser

    /** `PATCH /admin/users/{id}` with only `displayName` (1 to 50 characters). */
    suspend fun rename(userId: String, displayName: String): AdminUser

    suspend fun deleteUser(userId: String)

    /** Single-use reset token for the `reset` QR code (valid 24 hours). */
    suspend fun resetPassword(userId: String): QrGrant

    /** [shortCode] is what the Guest shows on their phone, typed by the Admin. */
    suspend fun promote(userId: String, role: Role, shortCode: String)

    suspend fun cancelPromotion(userId: String)

    /** All device sessions of the server. */
    suspend fun devices(): List<AdminDeviceSession>

    suspend fun revokeDevice(deviceId: String)

    suspend fun createInvite(role: Role): Invite

    suspend fun invites(): List<Invite>

    suspend fun cancelInvite(inviteId: String)

    suspend fun publicInvite(): PublicInvite

    suspend fun updatePublicInvite(enabled: Boolean, guestLimit: Int?): PublicInvite

    /** New token: printed sheets stop working. The answer carries the token, for the PDF. */
    suspend fun regeneratePublicInvite(): PublicInvite

    /** Streams the server backup into [write] (a file), without keeping it in memory. */
    suspend fun downloadBackup(write: suspend (ByteReadChannel) -> Unit)

    /** Sends a backup file ([body], [size] bytes) to the server. */
    suspend fun importBackup(mode: ServerImportMode, size: Long?, body: () -> ByteReadChannel)

    suspend fun settings(): AdminSettings

    suspend fun updateSettings(settings: AdminSettings): AdminSettings
}

/** [AdminApi] over the shared [ServerClient]. */
class KtorAdminApi(private val client: ServerClient) : AdminApi {
    private val http get() = client.http

    // Same client = same API: the screens key their view models on it, so a new client (connection
    // changed, the old one closed) gets new view models, and opening the screen again reuses them.
    override fun equals(other: Any?): Boolean = other is KtorAdminApi && other.client === client

    override fun hashCode(): Int = client.hashCode()

    override suspend fun serverInfo(): ServerInfo = apiCall { http.get("server-info").body() }

    override suspend fun users(): List<AdminUser> = apiCall { http.get("admin/users").body() }

    override suspend fun changeRole(userId: String, role: Role): AdminUser =
        patchUser(userId, UpdateUserRequest(role = WireRole.of(role)))

    override suspend fun setDisabled(userId: String, disabled: Boolean): AdminUser =
        patchUser(userId, UpdateUserRequest(disabled = disabled))

    override suspend fun rename(userId: String, displayName: String): AdminUser =
        patchUser(userId, UpdateUserRequest(displayName = displayName))

    private suspend fun patchUser(userId: String, request: UpdateUserRequest): AdminUser = apiCall {
        http.patch("admin/users/${userId.path()}") { json(request) }.body()
    }

    override suspend fun deleteUser(userId: String) {
        apiCall { http.delete("admin/users/${userId.path()}") }
    }

    override suspend fun resetPassword(userId: String): QrGrant =
        apiCall { http.post("admin/users/${userId.path()}/reset").body() }

    override suspend fun promote(userId: String, role: Role, shortCode: String) {
        apiCall { http.post("admin/users/${userId.path()}/promote") { json(PromoteRequest(WireRole.of(role), shortCode)) } }
    }

    override suspend fun cancelPromotion(userId: String) {
        apiCall { http.delete("admin/users/${userId.path()}/promote") }
    }

    override suspend fun devices(): List<AdminDeviceSession> = apiCall { http.get("admin/devices").body() }

    override suspend fun revokeDevice(deviceId: String) {
        apiCall { http.delete("admin/devices/${deviceId.path()}") }
    }

    override suspend fun createInvite(role: Role): Invite =
        apiCall { http.post("admin/invites") { json(CreateInviteRequest(WireRole.of(role))) }.body() }

    override suspend fun invites(): List<Invite> = apiCall { http.get("admin/invites").body() }

    override suspend fun cancelInvite(inviteId: String) {
        apiCall { http.delete("admin/invites/${inviteId.path()}") }
    }

    override suspend fun publicInvite(): PublicInvite = apiCall { http.get("admin/public-invite").body() }

    override suspend fun updatePublicInvite(enabled: Boolean, guestLimit: Int?): PublicInvite = apiCall {
        http.put("admin/public-invite") {
            // guestLimit null = no limit: sent as an explicit null
            json(
                buildJsonObject {
                    put("enabled", JsonPrimitive(enabled))
                    put("guestLimit", guestLimit?.let(::JsonPrimitive) ?: JsonNull)
                },
            )
        }.body()
    }

    override suspend fun regeneratePublicInvite(): PublicInvite =
        apiCall { http.post("admin/public-invite/regenerate").body() }

    override suspend fun downloadBackup(write: suspend (ByteReadChannel) -> Unit) {
        apiCall {
            http.prepareGet("admin/backup") {
                timeout { requestTimeoutMillis = Long.MAX_VALUE }
            }.execute { write(it.bodyAsChannel()) }
        }
    }

    override suspend fun importBackup(mode: ServerImportMode, size: Long?, body: () -> ByteReadChannel) {
        apiCall {
            http.post("admin/backup") {
                parameter("mode", mode.query)
                timeout { requestTimeoutMillis = Long.MAX_VALUE }
                setBody(BackupUpload(size, body))
            }
        }
    }

    override suspend fun settings(): AdminSettings = apiCall { http.get("admin/settings").body() }

    override suspend fun updateSettings(settings: AdminSettings): AdminSettings = apiCall {
        http.put("admin/settings") { json(settingsBody(settings)) }.body()
    }

    private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}

/** Settings body: both fields, retention null = keep forever (explicit null). */
internal fun settingsBody(settings: AdminSettings) = buildJsonObject {
    put("serverName", JsonPrimitive(settings.serverName))
    put("movementAuthorRetentionDays", settings.movementAuthorRetentionDays?.let(::JsonPrimitive) ?: JsonNull)
}

private fun String.path(): String = encodeURLPathPart()

/** A streamed backup upload (the file is never fully in memory). */
private class BackupUpload(
    private val size: Long?,
    private val open: () -> ByteReadChannel,
) : io.ktor.http.content.OutgoingContent.ReadChannelContent() {
    override val contentType: ContentType = ContentType.Application.OctetStream
    override val contentLength: Long? get() = size
    override fun readFrom(): ByteReadChannel = open()
}
