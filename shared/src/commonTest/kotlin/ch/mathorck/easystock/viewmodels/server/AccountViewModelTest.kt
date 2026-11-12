package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.server.PendingPromotion
import ch.mathorck.easystock.auth.ServerSessionEvents
import ch.mathorck.easystock.server.AccountApi
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.CompletePromotionRequest
import ch.mathorck.easystock.server.ConnectError
import ch.mathorck.easystock.server.MeAnswer
import ch.mathorck.easystock.server.SessionUser
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAccountApi : AccountApi {
    var me = MeAnswer("u1", "Visitor", "guest", "guest", shortCode = "K7Q2", pendingPromotion = PendingPromotion("member"))
    var meError: Exception? = null
    var logoutError: Exception? = null
    var promotionError: Exception? = null
    var logouts = 0
    var alertsError: Exception? = null
    val alertCalls = mutableListOf<Boolean>()
    val promotions = mutableListOf<CompletePromotionRequest>()
    val promoted = TokenAnswer("a2", "r2", user = SessionUser("u1", "Ann", "member", "human"))

    override suspend fun me(): MeAnswer {
        meError?.let { throw it }
        return me
    }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        alertCalls += enabled
        alertsError?.let { throw it }
    }

    var nameError: Exception? = null
    var passwordError: Exception? = null
    val names = mutableListOf<String>()
    val passwords = mutableListOf<Pair<String, String>>()

    override suspend fun setDisplayName(name: String): MeAnswer {
        names += name
        nameError?.let { throw it }
        return me.copy(displayName = name)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        passwords += currentPassword to newPassword
        passwordError?.let { throw it }
    }

    override suspend fun logout() {
        logouts++
        logoutError?.let { throw it }
    }

    override suspend fun completePromotion(request: CompletePromotionRequest): TokenAnswer {
        promotions += request
        promotionError?.let { throw it }
        return promoted
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val api = FakeAccountApi()
    private var pending = 0
    private val stored = mutableListOf<MeAnswer>()
    private val promotedWith = mutableListOf<TokenAnswer>()
    private var wipes = 0
    private var syncs = 0
    private var leftAfterSync = 0
    private var leavingDuringLogout = false
    private val order = mutableListOf<String>()
    private var beforeLogoutError: Exception? = null

    private fun vm() = AccountViewModel(
        api = api,
        pendingChanges = { pending },
        syncFirst = {
            syncs++
            leftAfterSync
        },
        onMe = { stored += it },
        onPromoted = { promotedWith += it },
        wipe = {
            leavingDuringLogout = ServerSessionEvents.leaving
            wipes++
        },
        beforeLogout = {
            order += "beforeLogout:${api.logouts}"
            beforeLogoutError?.let { throw it }
        },
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        ServerSessionEvents.leaving = false
        Dispatchers.resetMain()
    }

    @Test
    fun meIsShownAndStored() {
        val vm = vm()
        vm.refresh()
        assertEquals(api.me, vm.state.value.me)
        assertEquals(listOf(api.me), stored)
    }

    @Test
    fun alertsSwitchIsSentAndKept() {
        api.me = MeAnswer("u1", "Vera", "viewer", "full", alertsEnabled = null)
        val vm = vm()
        vm.refresh()
        var saved = 0
        vm.setAlerts(true) { saved++ }
        assertEquals(listOf(true), api.alertCalls)
        assertEquals(true, vm.state.value.me?.alertsEnabled)
        assertFalse(vm.state.value.alertsBusy)
        assertEquals(1, saved)
        assertNull(vm.state.value.error)
    }

    @Test
    fun alertsSwitchRollsBackOnFailure() {
        api.me = MeAnswer("u1", "Vera", "viewer", "full", alertsEnabled = false)
        val vm = vm()
        vm.refresh()
        api.alertsError = ApiException(ApiError.NETWORK)
        var saved = 0
        vm.setAlerts(true) { saved++ }
        assertEquals(false, vm.state.value.me?.alertsEnabled)
        assertEquals(ConnectError.NETWORK, vm.state.value.error)
        assertFalse(vm.state.value.alertsBusy)
        assertEquals(0, saved)
    }

    @Test
    fun alertsSwitchNeedsTheAccount() {
        api.meError = ApiException(ApiError.NETWORK)
        val vm = vm()
        vm.refresh()
        vm.setAlerts(true)
        assertTrue(api.alertCalls.isEmpty())
    }

    @Test
    fun meOfflineKeepsTheScreen() {
        api.meError = ApiException(ApiError.NETWORK)
        val vm = vm()
        vm.refresh()
        assertNull(vm.state.value.me)
        assertEquals(ConnectError.NETWORK, vm.state.value.meError)
    }

    @Test
    fun aLogoutTellsTheServerThenWipes() {
        val vm = vm()
        vm.askLogout(guest = true)
        assertEquals(LogoutStep.Confirm(0, guest = true), vm.state.value.logout)

        vm.confirmLogout()
        assertEquals(1, api.logouts)
        assertEquals(1, wipes)
        // A 401 while leaving is not reported as a revoked session
        assertTrue(leavingDuringLogout)
        assertFalse(ServerSessionEvents.leaving)
        assertEquals(LogoutStep.Done, vm.state.value.logout)
    }

    @Test
    fun thePushAddressIsDroppedBeforeTheLogout() {
        val vm = vm()
        vm.askLogout(guest = false)
        vm.confirmLogout()
        // Before the logout call, while the session still works
        assertEquals(listOf("beforeLogout:0"), order)
        assertEquals(1, api.logouts)
        assertEquals(LogoutStep.Done, vm.state.value.logout)
    }

    @Test
    fun aFailedPushUnregisterDoesNotStopTheLogout() {
        beforeLogoutError = IllegalStateException("offline")
        val vm = vm()
        vm.askLogout(guest = false)
        vm.confirmLogout()
        assertEquals(1, api.logouts)
        assertEquals(1, wipes)
    }

    @Test
    fun pendingChangesAreCounted() {
        pending = 3
        val vm = vm()
        vm.askLogout(guest = false)
        assertEquals(LogoutStep.Confirm(3, guest = false), vm.state.value.logout)
        vm.cancelLogout()
        assertNull(vm.state.value.logout)
        assertEquals(0, wipes)
    }

    @Test
    fun syncThenLogoutLogsOutWhenEverythingIsSent() {
        pending = 2
        val vm = vm()
        vm.askLogout(guest = false)
        vm.syncThenLogout()
        assertEquals(1, syncs)
        assertEquals(1, api.logouts)
        assertEquals(1, wipes)
        assertEquals(LogoutStep.Done, vm.state.value.logout)
    }

    @Test
    fun syncThenLogoutAsksAgainWhenChangesAreLeft() {
        pending = 2
        leftAfterSync = 1
        val vm = vm()
        vm.askLogout(guest = false)
        vm.syncThenLogout()
        assertEquals(LogoutStep.Confirm(1, guest = false, syncFailed = true), vm.state.value.logout)
        assertEquals(0, api.logouts)
        assertEquals(0, wipes)
    }

    @Test
    fun aRevokedSessionStillWipes() {
        api.logoutError = ApiException(ApiError.SESSION_REVOKED, 401, "session-revoked")
        val vm = vm()
        vm.askLogout(guest = false)
        vm.confirmLogout()
        assertEquals(1, wipes)
        assertEquals(LogoutStep.Done, vm.state.value.logout)
    }

    @Test
    fun offlineTheUserChooses() {
        api.logoutError = ApiException(ApiError.NETWORK)
        val vm = vm()
        vm.askLogout(guest = false)
        vm.confirmLogout()
        assertEquals(LogoutStep.Offline, vm.state.value.logout)
        assertEquals(0, wipes)

        vm.stayConnected()
        assertNull(vm.state.value.logout)
        assertFalse(ServerSessionEvents.leaving)

        vm.askLogout(guest = false)
        vm.confirmLogout()
        vm.logoutOffline()
        assertEquals(1, wipes)
        assertEquals(LogoutStep.Done, vm.state.value.logout)
    }

    @Test
    fun aPromotionSavesTheNewTokens() {
        val vm = vm()
        vm.openPromotion()
        vm.promotionUsernameChanged(" ann ")
        vm.promotionPasswordChanged("short")
        vm.submitPromotion()
        assertEquals(setOf(FormField.PASSWORD), vm.state.value.promotion?.invalidFields)
        assertTrue(api.promotions.isEmpty())

        vm.promotionPasswordChanged("long enough!")
        vm.submitPromotion()
        assertEquals(listOf(CompletePromotionRequest("ann", "long enough!")), api.promotions)
        assertEquals(listOf(api.promoted), promotedWith)
        assertNull(vm.state.value.promotion)
    }

    @Test
    fun aTakenUsernameKeepsTheForm() {
        api.promotionError = ApiException(ApiError.CONFLICT, 409, "username-taken")
        val vm = vm()
        vm.openPromotion()
        vm.promotionUsernameChanged("ann")
        vm.promotionPasswordChanged("long enough!")
        vm.submitPromotion()
        assertEquals(ConnectError.USERNAME_TAKEN, vm.state.value.error)
        assertNotNull(vm.state.value.promotion)
        assertFalse(vm.state.value.promotion!!.busy)
    }

    @Test
    fun anExpiredPromotionClosesTheForm() {
        api.promotionError = ApiException(ApiError.CONFLICT, 410, "promotion-expired")
        val vm = vm()
        vm.openPromotion()
        vm.promotionUsernameChanged("ann")
        vm.promotionPasswordChanged("long enough!")
        vm.submitPromotion()
        assertEquals(ConnectError.CODE_EXPIRED, vm.state.value.error)
        assertNull(vm.state.value.promotion)
        assertTrue(promotedWith.isEmpty())
    }

    @Test
    fun theNewNameReplacesTheAccountAndIsStored() {
        val vm = vm()
        vm.refresh()
        stored.clear()
        vm.openName()
        assertEquals("Visitor", vm.state.value.nameForm?.text)
        vm.nameChanged("  Ann  ")
        vm.submitName()
        assertEquals(listOf("Ann"), api.names)
        assertEquals("Ann", vm.state.value.me?.displayName)
        assertEquals("Ann", stored.single().displayName)
        assertNull(vm.state.value.nameForm)
    }

    @Test
    fun anInvalidNameIsNotSent() {
        val vm = vm()
        vm.refresh()
        vm.openName()
        vm.nameChanged("   ")
        vm.submitName()
        assertTrue(api.names.isEmpty())
        assertTrue(vm.state.value.nameForm!!.invalid)
        vm.nameChanged("x".repeat(51))
        vm.submitName()
        assertTrue(api.names.isEmpty())
    }

    @Test
    fun aFailedNameKeepsTheFormAndShowsTheError() {
        api.nameError = ApiException(ApiError.NETWORK)
        val vm = vm()
        vm.refresh()
        vm.openName()
        vm.nameChanged("Ann")
        vm.submitName()
        assertEquals(ConnectError.NETWORK, vm.state.value.error)
        assertNotNull(vm.state.value.nameForm)
        assertFalse(vm.state.value.nameForm!!.busy)
        assertEquals("Visitor", vm.state.value.me?.displayName)
    }

    @Test
    fun thePasswordIsChangedAndTheUserIsTold() {
        val vm = vm()
        vm.openPassword()
        vm.currentPasswordChanged("old password")
        vm.newPasswordChanged("new password 1")
        vm.submitPassword()
        assertEquals(listOf("old password" to "new password 1"), api.passwords)
        assertNull(vm.state.value.passwordForm)
        assertTrue(vm.state.value.passwordChanged)
        assertEquals(0, wipes)
        vm.dismissPasswordChanged()
        assertFalse(vm.state.value.passwordChanged)
    }

    @Test
    fun aTooShortNewPasswordOrEmptyCurrentIsNotSent() {
        val vm = vm()
        vm.openPassword()
        vm.newPasswordChanged("short")
        vm.submitPassword()
        assertTrue(api.passwords.isEmpty())
        val form = vm.state.value.passwordForm!!
        assertTrue(form.currentEmpty)
        assertEquals(setOf(FormField.PASSWORD), form.invalidFields)
    }

    @Test
    fun aWrongCurrentPasswordIsAFieldErrorNotADialog() {
        api.passwordError = ApiException(ApiError.INVALID, 400, "invalid-credentials")
        val vm = vm()
        vm.openPassword()
        vm.currentPasswordChanged("nope")
        vm.newPasswordChanged("new password 1")
        vm.submitPassword()
        val form = vm.state.value.passwordForm!!
        assertTrue(form.wrongCurrent)
        assertFalse(form.busy)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.passwordChanged)
        vm.currentPasswordChanged("again")
        assertFalse(vm.state.value.passwordForm!!.wrongCurrent)
    }

    @Test
    fun aRefusedNewPasswordKeepsTheFormAndShowsTheError() {
        api.passwordError = ApiException(ApiError.INVALID, 400, "validation-failed")
        val vm = vm()
        vm.openPassword()
        vm.currentPasswordChanged("old password")
        vm.newPasswordChanged("new password 1")
        vm.submitPassword()
        assertEquals(ConnectError.INVALID, vm.state.value.error)
        assertNotNull(vm.state.value.passwordForm)
        assertFalse(vm.state.value.passwordChanged)
    }
}
