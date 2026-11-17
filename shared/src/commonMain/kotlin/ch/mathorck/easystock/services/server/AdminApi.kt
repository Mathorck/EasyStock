package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.AdminDeviceSession
import ch.mathorck.easystock.data.dto.server.AdminSettings
import ch.mathorck.easystock.data.dto.server.AdminUser
import ch.mathorck.easystock.data.dto.server.Invite
import ch.mathorck.easystock.data.dto.server.PublicInvite
import ch.mathorck.easystock.data.dto.server.QrGrant
import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.enums.server.ServerImportMode
import io.ktor.client.call.body
import io.ktor.client.request.put
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.ByteReadChannel
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

/** Settings body: the server name only. */
internal fun settingsBody(settings: AdminSettings) = buildJsonObject {
    put("serverName", JsonPrimitive(settings.serverName))
}

internal fun String.path(): String = encodeURLPathPart()
