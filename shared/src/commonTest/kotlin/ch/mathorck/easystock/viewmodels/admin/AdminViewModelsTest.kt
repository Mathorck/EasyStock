package ch.mathorck.easystock.viewmodels.admin

import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.AdminDevice
import ch.mathorck.easystock.server.AdminSettings
import ch.mathorck.easystock.server.AdminUser
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.Invite
import ch.mathorck.easystock.server.JoinCode
import ch.mathorck.easystock.server.JoinKind
import ch.mathorck.easystock.server.LocalFile
import ch.mathorck.easystock.server.ProblemTypes
import ch.mathorck.easystock.server.PublicInvite
import ch.mathorck.easystock.server.QrGrant
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.ServerFileStore
import ch.mathorck.easystock.server.ServerImportMode
import ch.mathorck.easystock.server.ServerInfo
import ch.mathorck.easystock.server.UserKind
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortCodeTest {
    @Test
    fun caseSpacesAndDashesAreIgnored() {
        assertEquals("K74QX", normalizeShortCode(" k7-4qx "))
        assertTrue(shortCodesMatch("K7-4QX", "k7 4qx"))
        assertTrue(shortCodesMatch("K7-4QX", "K74QX"))
        assertTrue(shortCodesMatch("K7-4QX", "k7–4qx".replace('–', '-')))
    }

    @Test
    fun otherCodesDoNotMatch() {
        assertFalse(shortCodesMatch("K7-4QX", "K7-4QY"))
        assertFalse(shortCodesMatch("K7-4QX", "K7-4Q"))
        assertFalse(shortCodesMatch("K7-4QX", ""))
        assertFalse(shortCodesMatch(null, "K74QX"))
        assertFalse(shortCodesMatch("--", "--"))
    }

    @Test
    fun promoteOnlyWhenTheCodesMatch() {
        assertFalse(PromoteForm("K7-4QX").canPromote)
        assertFalse(PromoteForm("K7-4QX", "K7-4QZ").canPromote)
        assertTrue(PromoteForm("K7-4QX", "k74qx").canPromote)
        assertFalse(PromoteForm("K7-4QX", "k74qx", Role.GUEST).canPromote)
        assertFalse(PromoteForm(null, "k74qx").canPromote)
    }
}

class ServerImportFormTest {
    private val file = LocalFile("/c/b", "b.easystock", 10, "application/octet-stream")

    @Test
    fun mergeNeedsNoWord() = assertTrue(ServerImportForm(file).canImport("REPLACE"))

    @Test
    fun replaceNeedsTheTypedWord() {
        val form = ServerImportForm(file, ServerImportMode.REPLACE)
        assertFalse(form.canImport("REMPLACER"))
        assertFalse(form.copy(typed = "REMPLACE").canImport("REMPLACER"))
        assertTrue(form.copy(typed = " remplacer ").canImport("REMPLACER"))
        assertFalse(form.copy(typed = "").canImport(""))
    }
}

class QrGrantTextTest {
    private val connection = ServerConnection("https://192.168.1.10:8443", "ab".repeat(32))

    @Test
    fun builtFromTheTokenWithTheConnection() {
        val text = assertNotNull(QrGrant(token = "tok").qrText(connection, JoinKind.RESET))
        val code = JoinCode.parse(text)
        assertEquals(JoinKind.RESET, code.kind)
        assertEquals("tok", code.token)
        assertEquals(connection.serverUrl, code.serverUrl)
        assertEquals(connection.fingerprint, code.fingerprint)
    }

    @Test
    fun serverCodeOnlyWhenValidAndOfTheKind() {
        val invite = JoinCode("https://192.168.1.10:8443", JoinKind.INVITE, "t").toQrText()
        assertEquals(invite, QrGrant(qr = invite).qrText(connection, JoinKind.INVITE))
        assertNull(QrGrant(qr = invite).qrText(connection, JoinKind.RESET))
        assertNull(QrGrant(qr = "easystock://invite?server=x&token=y").qrText(connection, JoinKind.INVITE))
        assertNull(QrGrant().qrText(connection, JoinKind.INVITE))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminUsersViewModelTest {
    private val api = FakeAdminApi()
    private lateinit var vm: AdminUsersViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = AdminUsersViewModel(api)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nothingIsLoadedBeforeRefresh() {
        assertEquals(0, api.userReads)
        vm.refresh()
        assertEquals(1, api.userReads)
        val users = assertNotNull(vm.state.value.users.data)
        // Pending promotion first, then full accounts, then Guests
        assertEquals(listOf("g2", "a", "g1"), users.map { it.id })
    }

    @Test
    fun promoteIsRefusedUntilTheCodesMatch() {
        vm.refresh()
        vm.startPromotion("g1")
        assertEquals("AB-CDE", vm.state.value.promote?.expectedCode)
        vm.typePromoteCode("AB-CDF")
        vm.promote("g1")
        assertNull(api.promoted)

        vm.typePromoteCode("ab cde")
        vm.choosePromoteRole(Role.VIEWER)
        vm.promote("g1")
        assertEquals(Triple("g1", Role.VIEWER, "ab cde"), api.promoted)
        assertNull(vm.state.value.promote)
        assertEquals(UserMessage.PROMOTION_PENDING, vm.state.value.message)
    }

    @Test
    fun aFailedActionKeepsTheListAndShowsTheProblem() {
        vm.refresh()
        api.fail = ApiException(ApiError.CONFLICT, 409, ProblemTypes.LAST_ADMIN)
        vm.changeRole("a", Role.VIEWER)
        val failure = assertNotNull(vm.state.value.failure)
        assertEquals(ApiError.CONFLICT, failure.error)
        assertEquals(ProblemTypes.LAST_ADMIN, failure.problemType)
        assertFalse(vm.state.value.busy)
        assertEquals(3, vm.state.value.users.data?.size)
    }

    @Test
    fun aFailedLoadCanBeRetried() {
        api.fail = ApiException(ApiError.NETWORK)
        vm.refresh()
        assertEquals(ApiError.NETWORK, vm.state.value.users.error)
        assertNull(vm.state.value.users.data)
        api.fail = null
        vm.retry()
        assertNull(vm.state.value.users.error)
        assertEquals(3, vm.state.value.users.data?.size)
    }

    @Test
    fun resetKeepsTheGrantForTheUser() {
        vm.refresh()
        vm.resetPassword("a")
        assertEquals("a", vm.state.value.resetFor)
        assertEquals("reset-token", vm.state.value.reset?.token)
        vm.closeReset()
        assertNull(vm.state.value.reset)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ServerBackupViewModelTest {
    private val api = FakeAdminApi()
    private val files = FakeFileStore()
    private lateinit var vm: ServerBackupViewModel
    private val file = LocalFile("/c/b", "b.easystock", 10, ServerBackupViewModel.MIME)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ServerBackupViewModel(api, files) { "backup.easystock" }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun replaceIsNotSentWithoutTheWord() {
        vm.picked(file)
        vm.chooseMode(ServerImportMode.REPLACE)
        vm.import("REPLACE")
        assertNull(api.imported)

        vm.typeConfirmation("replac")
        vm.import("REPLACE")
        assertNull(api.imported)

        vm.typeConfirmation("replace")
        vm.import("REPLACE")
        assertEquals(ServerImportMode.REPLACE, api.imported)
        assertNull(vm.state.value.import)
        assertEquals(ServerBackupMessage.IMPORTED, vm.state.value.message)
    }

    @Test
    fun changingTheModeClearsTheWord() {
        vm.picked(file)
        vm.chooseMode(ServerImportMode.REPLACE)
        vm.typeConfirmation("REPLACE")
        vm.chooseMode(ServerImportMode.MERGE)
        vm.chooseMode(ServerImportMode.REPLACE)
        assertEquals("", vm.state.value.import?.typed)
    }

    @Test
    fun mergeIsSentRightAway() {
        vm.picked(file)
        vm.import("REPLACE")
        assertEquals(ServerImportMode.MERGE, api.imported)
    }

    @Test
    fun downloadKeepsTheFile() {
        vm.download()
        assertEquals("backup.easystock", vm.state.value.downloaded?.name)
        assertEquals(ServerBackupMessage.DOWNLOADED, vm.state.value.message)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminSettingsViewModelTest {
    private val api = FakeAdminApi()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun alertRoleReplacesBothSpellings() {
        api.settings = AdminSettings(movementAuthorRetentionDays = 90, alertRoles = listOf("admin", "editor"))
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        vm.setAlertRole(Role.EDITOR, false)
        assertEquals(listOf("admin"), api.settings.alertRoles)
        vm.setAlertRole(Role.EDITOR, true)
        assertEquals(listOf("admin", "member"), api.settings.alertRoles)
        assertEquals(setOf(Role.ADMIN, Role.EDITOR), vm.state.value.alertRoles)
    }

    @Test
    fun noAlertRolesWhenTheServerHasNone() {
        api.settings = AdminSettings(movementAuthorRetentionDays = null)
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        assertNull(vm.state.value.alertRoles)
        vm.setRetention(30)
        assertEquals(30, api.settings.movementAuthorRetentionDays)
        assertNull(api.settings.alertRoles)
    }
}

private class FakeAdminApi : AdminApi {
    var fail: Exception? = null
    var userReads = 0
    var promoted: Triple<String, Role, String>? = null
    var imported: ServerImportMode? = null
    var settings = AdminSettings()

    private val users = listOf(
        AdminUser("g1", "Zed", null, "guest", UserKind.GUEST, shortCode = "AB-CDE"),
        AdminUser("a", "Ana", "ana", "admin", UserKind.FULL),
        AdminUser("g2", "Bob", null, "guest", UserKind.GUEST, shortCode = "XY-ZZZ", pendingPromotion = ch.mathorck.easystock.server.PendingPromotion("member")),
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
    override suspend fun devices(): List<AdminDevice> = emptyList()
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
        return settings
    }
    override suspend fun updateSettings(settings: AdminSettings): AdminSettings {
        check()
        this.settings = settings
        return settings
    }
}

private class FakeFileStore : ServerFileStore {
    override suspend fun receive(name: String, mimeType: String, channel: ByteReadChannel) = LocalFile("/c/$name", name, 3, mimeType)
    override fun read(file: LocalFile) = ByteReadChannel(byteArrayOf(1))
    override suspend fun invitePdf(name: String, qrText: String, serverName: String, line: String) = LocalFile("/c/$name", name, 1, "application/pdf")
    override suspend fun clear() = Unit
}
