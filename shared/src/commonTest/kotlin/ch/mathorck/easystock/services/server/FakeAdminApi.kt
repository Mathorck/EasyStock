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
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred

internal class FakeAdminApi : AdminApi {
    var fail: Exception? = null
    var userReads = 0
    var promoted: Triple<String, Role, String>? = null
    var renamed: Pair<String, String>? = null
    var imported: ServerImportMode? = null
    var settings = AdminSettings()

    private val users = listOf(
        AdminUser("g1", "Zed", null, "guest", UserKind.GUEST, shortCode = "AB-CDE"),
        AdminUser("a", "Ana", "ana", "admin", UserKind.FULL),
        AdminUser("g2", "Bob", null, "guest", UserKind.GUEST, shortCode = "XY-ZZZ", pendingPromotion = ch.mathorck.easystock.data.dto.server.PendingPromotion("member")),
    )

    private fun check() = fail?.let { throw it }

    override suspend fun serverInfo() = ServerInfo(serverName = "Test")
    override suspend fun users(): List<AdminUser> {
        userReads++
        check()
        return users
    }
    override suspend fun changeRole(userId: String, role: Role): AdminUser {
        check()
        return users.first { it.id == userId }
    }
    override suspend fun setDisabled(userId: String, disabled: Boolean): AdminUser {
        check()
        return users.first { it.id == userId }
    }
    override suspend fun rename(userId: String, displayName: String): AdminUser {
        check()
        renamed = userId to displayName
        return users.first { it.id == userId }
    }
    override suspend fun deleteUser(userId: String) = check() ?: Unit
    override suspend fun resetPassword(userId: String): QrGrant {
        check()
        return QrGrant(token = "reset-token")
    }
    override suspend fun promote(userId: String, role: Role, shortCode: String) {
        check()
        promoted = Triple(userId, role, shortCode)
    }
    override suspend fun cancelPromotion(userId: String) = check() ?: Unit
    override suspend fun devices(): List<AdminDeviceSession> = emptyList()
    override suspend fun revokeDevice(deviceId: String) = check() ?: Unit
    override suspend fun createInvite(role: Role) = Invite("i", "member", token = "t")
    override suspend fun invites(): List<Invite> = emptyList()
    override suspend fun cancelInvite(inviteId: String) = Unit
    override suspend fun publicInvite() = PublicInvite()
    override suspend fun updatePublicInvite(enabled: Boolean, guestLimit: Int?) = PublicInvite(enabled, guestLimit)
    override suspend fun regeneratePublicInvite() = PublicInvite(true, token = "new")
    override suspend fun downloadBackup(write: suspend (ByteReadChannel) -> Unit) {
        check()
        write(ByteReadChannel(byteArrayOf(1, 2, 3)))
    }
    override suspend fun importBackup(mode: ServerImportMode, size: Long?, body: () -> ByteReadChannel) {
        check()
        body()
        imported = mode
    }
    override suspend fun settings(): AdminSettings {
        check()
        // Read before waiting, like a server that answered before a later save
        val read = settings
        settingsGate?.await()
        return read
    }
    override suspend fun updateSettings(settings: AdminSettings): AdminSettings {
        settingsWrites++
        check()
        this.settings = settings
        return settingsAnswer ?: settings
    }

    /** When set, a settings read waits for it. */
    var settingsGate: CompletableDeferred<Unit>? = null
    var settingsWrites = 0
    /** When set, what the server answers to a settings update. */
    var settingsAnswer: AdminSettings? = null
}
