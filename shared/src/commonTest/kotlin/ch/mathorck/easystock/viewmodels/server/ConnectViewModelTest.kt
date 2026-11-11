package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.ConnectError
import ch.mathorck.easystock.server.GuestSignupRequest
import ch.mathorck.easystock.server.JoinApi
import ch.mathorck.easystock.server.JoinCode
import ch.mathorck.easystock.server.JoinKind
import ch.mathorck.easystock.server.LoginRequest
import ch.mathorck.easystock.server.RedeemInviteRequest
import ch.mathorck.easystock.server.ResetPasswordRequest
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.ServerInfo
import ch.mathorck.easystock.server.SessionUser
import ch.mathorck.easystock.server.SetupRequest
import ch.mathorck.easystock.server.TokenAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ANSWER = TokenAnswer("access", "refresh", user = SessionUser("u1", "Ann", "member", "human"))

private class FakeJoinApi(val connection: ServerConnection) : JoinApi, AutoCloseable {
    var info: ServerInfo = ServerInfo(apiVersion = 1, setupDone = true, guestSignupEnabled = true)
    var infoError: ApiException? = null
    var authError: ApiException? = null
    val calls = mutableListOf<Any>()
    var closed = false

    override suspend fun serverInfo(): ServerInfo {
        calls += "server-info"
        infoError?.let { throw it }
        return info
    }

    private fun answer(request: Any): TokenAnswer {
        calls += request
        authError?.let { throw it }
        return ANSWER
    }

    override suspend fun login(request: LoginRequest) = answer(request)

    override suspend fun setup(request: SetupRequest) = answer(request)

    override suspend fun redeemInvite(request: RedeemInviteRequest) = answer(request)

    override suspend fun guestSignup(request: GuestSignupRequest) = answer(request)

    override suspend fun resetPassword(request: ResetPasswordRequest) = answer(request)

    override fun close() {
        closed = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    private val apis = mutableListOf<FakeJoinApi>()
    private var setUpApi: (FakeJoinApi) -> Unit = {}
    private var empty = true
    private var erased = 0
    private val committed = mutableListOf<Pair<ServerConnection, TokenAnswer>>()
    private var commitResult = true

    private fun vm() = ConnectViewModel(
        joinApi = { FakeJoinApi(it).also(setUpApi).also(apis::add) },
        isPhoneEmpty = { empty },
        erase = {
            erased++
            empty = true
        },
        commit = { connection, answer ->
            committed += connection to answer
            commitResult
        },
        deviceName = "Pixel",
        appVersion = "1.0.0",
    )

    private val api get() = apis.last()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun code(kind: JoinKind, url: String = "https://nas.example", fp: String? = null) = JoinCode(url, kind, "tok", fp)

    private fun ConnectViewModel.fill(username: String = "ann", displayName: String = "Ann", password: String = "long enough!") {
        usernameChanged(username)
        displayNameChanged(displayName)
        passwordChanged(password)
    }

    @Test
    fun aTypedAddressLogsIn() {
        val vm = vm()
        vm.addressChanged("nas.example:8443")
        vm.submitAddress()

        val form = assertIs<ConnectStep.Form>(vm.state.value.step)
        assertEquals(FormMode.LOGIN, form.mode)
        assertEquals(ServerConnection("https://nas.example:8443"), api.connection)

        vm.fill(password = "short")
        vm.submitForm()

        assertEquals(LoginRequest("ann", "short", "Pixel"), api.calls.last())
        assertEquals(listOf(ServerConnection("https://nas.example:8443") to ANSWER), committed)
        assertEquals(ConnectStep.Done, vm.state.value.step)
        assertTrue(api.closed)
    }

    @Test
    fun anInvalidAddressMakesNoCall() {
        val vm = vm()
        vm.addressChanged("ftp://nas")
        vm.submitAddress()

        assertTrue(vm.state.value.addressInvalid)
        assertEquals(ConnectStep.Choose, vm.state.value.step)
        assertTrue(apis.isEmpty())
    }

    @Test
    fun noHttpsAnswerOffersHttpAfterAWarning() {
        setUpApi = { if (it.connection.isHttps) it.infoError = ApiException(ApiError.NETWORK) }
        val vm = vm()
        vm.addressChanged("192.168.1.20:8080")
        vm.submitAddress()

        val failed = assertIs<ConnectStep.HttpsFailed>(vm.state.value.step)
        assertEquals("http://192.168.1.20:8080", failed.httpUrl)

        vm.tryHttp()
        assertIs<ConnectStep.HttpWarning>(vm.state.value.step)
        // No HTTP call before the user accepts
        assertEquals(1, apis.size)

        vm.acceptHttp()
        assertIs<ConnectStep.Form>(vm.state.value.step)
        assertEquals(ServerConnection("http://192.168.1.20:8080"), api.connection)
        assertTrue(apis.first().closed)
    }

    @Test
    fun aTypedHttpsAddressWithoutAnswerIsANetworkError() {
        setUpApi = { it.infoError = ApiException(ApiError.NETWORK) }
        val vm = vm()
        vm.addressChanged("https://nas.example")
        vm.submitAddress()

        assertEquals(ConnectError.NETWORK, vm.state.value.error)
        assertEquals(ConnectStep.Choose, vm.state.value.step)
    }

    @Test
    fun anHttpCodeAsksFirst() {
        val vm = vm()
        vm.start(code(JoinKind.INVITE, url = "http://10.0.0.2:8080"))

        assertIs<ConnectStep.HttpWarning>(vm.state.value.step)
        assertTrue(apis.isEmpty())

        vm.acceptHttp()
        assertEquals(FormMode.INVITE, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
    }

    @Test
    fun theFingerprintOfTheCodePinsTheConnection() {
        val fp = "ab".repeat(32)
        val vm = vm()
        vm.start(code(JoinKind.INVITE, fp = fp))
        assertEquals(ServerConnection("https://nas.example", fp), api.connection)
        vm.fill()
        vm.submitForm()
        assertEquals(ServerConnection("https://nas.example", fp), committed.single().first)
    }

    @Test
    fun notAServer() {
        setUpApi = { it.infoError = ApiException(ApiError.NOT_FOUND, 404) }
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        assertEquals(ConnectError.NOT_A_SERVER, vm.state.value.error)
    }

    @Test
    fun anUnsupportedServer() {
        setUpApi = { it.info = it.info.copy(apiVersion = 2) }
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        assertEquals(ConnectError.API_UNSUPPORTED, vm.state.value.error)
        assertEquals(ConnectStep.Choose, vm.state.value.step)

        setUpApi = { it.info = it.info.copy(minAppVersion = "2.0.0") }
        vm.start(code(JoinKind.INVITE, url = "https://other.example"))
        assertEquals(ConnectError.APP_TOO_OLD, vm.state.value.error)
    }

    @Test
    fun theCodeMustFitTheServerSetup() {
        val vm = vm()
        vm.start(code(JoinKind.SETUP))
        assertEquals(ConnectError.SETUP_DONE, vm.state.value.error)

        setUpApi = { it.info = it.info.copy(setupDone = false) }
        vm.start(code(JoinKind.INVITE, url = "https://new.example"))
        assertEquals(ConnectError.NOT_SET_UP, vm.state.value.error)

        vm.start(code(JoinKind.SETUP, url = "https://new2.example"))
        assertNull(vm.state.value.error)
        assertEquals(FormMode.SETUP, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
    }

    @Test
    fun aPhoneWithDataMustBeErasedFirst() {
        empty = false
        val vm = vm()
        vm.start(code(JoinKind.INVITE))

        assertIs<ConnectStep.NotEmpty>(vm.state.value.step)
        // The code is not spent: only server-info was called
        assertEquals(listOf<Any>("server-info"), api.calls)

        vm.askErase()
        assertIs<ConnectStep.EraseConfirm>(vm.state.value.step)
        vm.cancelErase()
        assertIs<ConnectStep.NotEmpty>(vm.state.value.step)
        assertEquals(0, erased)

        vm.askErase()
        vm.confirmErase()
        assertEquals(1, erased)
        assertEquals(FormMode.INVITE, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
    }

    @Test
    fun aFailedEraseStaysOnTheConfirmation() {
        empty = false
        val vm = ConnectViewModel(
            joinApi = { FakeJoinApi(it) },
            isPhoneEmpty = { false },
            erase = {},
            commit = { _, _ -> true },
            deviceName = "Pixel",
            appVersion = "1.0.0",
        )
        vm.start(code(JoinKind.INVITE))
        vm.askErase()
        vm.confirmErase()
        assertTrue(assertIs<ConnectStep.EraseConfirm>(vm.state.value.step).failed)
    }

    @Test
    fun cancelLeavesNothing() {
        empty = false
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        vm.cancel()
        assertEquals(ConnectStep.Choose, vm.state.value.step)
        assertTrue(api.closed)
        assertEquals(0, erased)
        assertTrue(committed.isEmpty())
    }

    @Test
    fun aGuestCodeOffersBoth() {
        val vm = vm()
        vm.start(code(JoinKind.GUEST))
        assertIs<ConnectStep.GuestChoice>(vm.state.value.step)

        vm.chooseGuest()
        assertEquals(FormMode.GUEST, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
        vm.displayNameChanged("  Visitor ")
        vm.submitForm()
        assertEquals(GuestSignupRequest("tok", "Visitor", "Pixel"), api.calls.last())
        assertEquals(ConnectStep.Done, vm.state.value.step)
    }

    @Test
    fun aGuestCodeCanLogIn() {
        val vm = vm()
        vm.start(code(JoinKind.GUEST))
        vm.chooseLogin()
        assertEquals(FormMode.LOGIN, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
    }

    @Test
    fun noGuestWhenTheServerTurnedItOff() {
        setUpApi = { it.info = it.info.copy(guestSignupEnabled = false) }
        val vm = vm()
        vm.start(code(JoinKind.GUEST))
        vm.chooseGuest()
        assertIs<ConnectStep.GuestChoice>(vm.state.value.step)
    }

    @Test
    fun eachCodeSendsItsForm() {
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        vm.fill()
        vm.submitForm()
        assertEquals(RedeemInviteRequest("tok", "ann", "Ann", "long enough!", "Pixel"), api.calls.last())

        setUpApi = { it.info = it.info.copy(setupDone = false) }
        vm.start(code(JoinKind.SETUP))
        vm.fill(username = "admin")
        vm.submitForm()
        assertEquals(SetupRequest("tok", "admin", "Ann", "long enough!", "Pixel"), api.calls.last())

        setUpApi = {}
        vm.start(code(JoinKind.RESET))
        assertEquals(FormMode.RESET, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
        vm.passwordChanged("a new password")
        vm.submitForm()
        assertEquals(ResetPasswordRequest("tok", "a new password", "Pixel"), api.calls.last())
        assertEquals(3, committed.size)
    }

    @Test
    fun theFieldsAreCheckedBeforeSending() {
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        vm.fill(username = "a b", displayName = "  ", password = "123456789")
        vm.submitForm()

        assertEquals(setOf(FormField.USERNAME, FormField.DISPLAY_NAME, FormField.PASSWORD), vm.state.value.invalidFields)
        assertEquals(listOf<Any>("server-info"), api.calls)

        vm.passwordChanged("1234567890")
        assertEquals(setOf(FormField.USERNAME, FormField.DISPLAY_NAME), vm.state.value.invalidFields)
    }

    @Test
    fun aWrongPasswordKeepsTheForm() {
        setUpApi = { it.authError = ApiException(ApiError.SESSION_REVOKED, 401, "invalid-credentials") }
        val vm = vm()
        vm.addressChanged("nas.example")
        vm.submitAddress()
        vm.fill()
        vm.submitForm()

        assertEquals(ConnectError.WRONG_CREDENTIALS, vm.state.value.error)
        val form = assertIs<ConnectStep.Form>(vm.state.value.step)
        assertEquals(false, form.busy)
        assertTrue(committed.isEmpty())
    }

    @Test
    fun anExpiredCodeGoesBackToTheStart() {
        setUpApi = { it.authError = ApiException(ApiError.CONFLICT, 410, "invite-expired") }
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        vm.fill()
        vm.submitForm()

        assertEquals(ConnectError.CODE_EXPIRED, vm.state.value.error)
        assertEquals(ConnectStep.Choose, vm.state.value.step)
    }

    @Test
    fun anOldAppIsTold() {
        setUpApi = { it.authError = ApiException(ApiError.APP_TOO_OLD, 426, "app-too-old") }
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        vm.fill()
        vm.submitForm()
        assertEquals(ConnectError.APP_TOO_OLD, vm.state.value.error)
    }

    @Test
    fun aNetworkErrorKeepsTheForm() {
        setUpApi = { it.authError = ApiException(ApiError.NETWORK) }
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        vm.fill()
        vm.submitForm()
        assertEquals(ConnectError.NETWORK, vm.state.value.error)
        assertIs<ConnectStep.Form>(vm.state.value.step)
    }

    @Test
    fun aRefusedCommitIsReported() {
        commitResult = false
        val vm = vm()
        vm.start(code(JoinKind.INVITE))
        vm.fill()
        vm.submitForm()
        assertEquals(ConnectError.LOCAL, vm.state.value.error)
        assertEquals(ConnectStep.Choose, vm.state.value.step)
    }

    @Test
    fun aPastedLinkIsACode() {
        val vm = vm()
        vm.addressChanged("easystock://invite?server=https://nas.example&token=tok")
        vm.submitAddress()
        assertEquals(FormMode.INVITE, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
    }

    @Test
    fun thePasswordStaysOutOfTheLogs() {
        val vm = vm()
        vm.passwordChanged("secret password")
        assertTrue("secret" !in vm.state.value.toString())
    }

    @Test
    fun aScannedCode() {
        val vm = vm()
        vm.startScan()
        vm.scanned("easystock://join?u=https%3A%2F%2Fnas.example&k=invite&t=tok")
        assertEquals(FormMode.INVITE, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
        // The camera keeps reading: a second read is ignored
        vm.scanned("easystock://join?u=https%3A%2F%2Fother.example&k=invite&t=tok")
        assertEquals(1, apis.size)
    }

    @Test
    fun aScannedBackupCodeIsNotAServerCode() {
        val vm = vm()
        vm.startScan()
        vm.scanned("EZS1:something")
        assertEquals(ConnectError.CODE_INVALID, vm.state.value.error)
        assertEquals(ConnectStep.Choose, vm.state.value.step)
        assertTrue(apis.isEmpty())
    }

    @Test
    fun aCodeFromTheBackupScreenWaitsForConnect() {
        val vm = vm()
        vm.offer(JoinCode.parse("easystock://join?u=https%3A%2F%2Fnas.example&k=invite&t=tok"))
        assertIs<ConnectStep.ConfirmCode>(vm.state.value.step)
        assertTrue(apis.isEmpty())
        vm.confirmCode()
        assertEquals(FormMode.INVITE, assertIs<ConnectStep.Form>(vm.state.value.step).mode)
        assertEquals(1, apis.size)
    }

    @Test
    fun anHttpCodeFromTheBackupScreenWaitsInTheWarning() {
        val vm = vm()
        vm.offer(JoinCode.parse("easystock://join?u=http%3A%2F%2F192.168.1.20%3A8080&k=invite&t=tok"))
        assertIs<ConnectStep.HttpWarning>(vm.state.value.step)
        assertTrue(apis.isEmpty())
        vm.cancel()
        assertEquals(ConnectStep.Choose, vm.state.value.step)
        assertTrue(apis.isEmpty())
    }
}
