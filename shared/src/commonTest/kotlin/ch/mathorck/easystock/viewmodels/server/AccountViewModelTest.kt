package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.data.dto.server.CompletePromotionRequest
import ch.mathorck.easystock.data.dto.server.MeAnswer
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.enums.server.ConnectError
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.auth.ServerSessionEvents
import ch.mathorck.easystock.services.server.FakeAccountApi
import ch.mathorck.easystock.services.server.ProblemTypes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

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
        wipeAfterDelete = { backupUnsent ->
            leavingDuringLogout = ServerSessionEvents.leaving
            deleteWipes += backupUnsent
            deleteWipeError?.let { throw it }
        },
    )

    /** The wipes after a delete, with their `backupUnsent`. */
    private val deleteWipes = mutableListOf<Boolean>()
    private var deleteWipeError: Exception? = null

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        ServerSessionEvents.leaving = false
        ServerSessionEvents.revokedHandled()
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
    fun leavingIsResetWhileOfflineAndOnCancel() {
        api.logoutError = ApiException(ApiError.NETWORK)
        val vm = vm()
        vm.askLogout(guest = false)
        vm.confirmLogout()
        assertEquals(LogoutStep.Offline, vm.state.value.logout)
        // The user did not leave yet: a revoke meanwhile is reported
        assertFalse(ServerSessionEvents.leaving)
        vm.cancelLogout()
        assertNull(vm.state.value.logout)
        assertFalse(ServerSessionEvents.leaving)
    }

    @Test
    fun leavingIsResetWhenTheScreenClosesDuringTheLogout() {
        api.logoutGate = kotlinx.coroutines.CompletableDeferred()
        val store = androidx.lifecycle.ViewModelStore()
        val vm = vm()
        store.put("account", vm)
        vm.askLogout(guest = false)
        vm.confirmLogout()
        assertTrue(ServerSessionEvents.leaving)

        store.clear()

        assertFalse(ServerSessionEvents.leaving)
        assertEquals(0, wipes)
        // A later revoke is reported again
        ServerSessionEvents.sessionRevoked()
        assertTrue(ServerSessionEvents.revoked.value)
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

    // ---------- Delete my account ----------

    private fun askDelete(guest: Boolean = false, password: String = "secret password"): AccountViewModel {
        val vm = vm()
        vm.askDelete(guest)
        if (!guest) vm.deletePasswordChanged(password)
        return vm
    }

    @Test
    fun anAccountIsDeletedWithItsPasswordThenThePhoneIsWiped() {
        val vm = askDelete()
        vm.confirmDelete()
        assertEquals(listOf<String?>("secret password"), api.deletes)
        assertEquals(listOf(false), deleteWipes)
        assertTrue(leavingDuringLogout, "the revoke handler must stay quiet during the wipe")
        assertFalse(ServerSessionEvents.leaving)
        assertEquals(DeletePhase.DONE, vm.state.value.deleteForm?.phase)
        assertNull(vm.state.value.error)
        // Not a logout: no logout call, no logout wipe
        assertEquals(0, api.logouts)
        assertEquals(0, wipes)
    }

    @Test
    fun noChangesNoSync() {
        pending = 0
        askDelete().confirmDelete()
        assertEquals(0, syncs)
    }

    @Test
    fun pendingChangesAreSentFirst() {
        pending = 3
        leftAfterSync = 0
        askDelete().confirmDelete()
        assertEquals(1, syncs)
        assertEquals(1, api.deletes.size)
        assertEquals(listOf(false), deleteWipes)
    }

    @Test
    fun anEmptyPasswordIsNotSent() {
        val vm = askDelete(password = "")
        vm.confirmDelete()
        assertTrue(vm.state.value.deleteForm!!.passwordEmpty)
        assertEquals(DeletePhase.CONFIRM, vm.state.value.deleteForm?.phase)
        assertTrue(api.deletes.isEmpty())
    }

    @Test
    fun aWrongPasswordIsAFieldErrorAndKeepsEverything() {
        api.deleteError = ApiException(ApiError.INVALID, 400, "invalid-credentials")
        val vm = askDelete()
        vm.confirmDelete()
        val form = vm.state.value.deleteForm!!
        assertTrue(form.wrongPassword)
        assertEquals(DeletePhase.CONFIRM, form.phase)
        assertNull(vm.state.value.error)
        assertTrue(deleteWipes.isEmpty())
        assertFalse(ServerSessionEvents.leaving)
        // Typing again clears the error
        vm.deletePasswordChanged("other")
        assertFalse(vm.state.value.deleteForm!!.wrongPassword)
    }

    @Test
    fun theLastAdminIsToldAndNothingIsWiped() {
        api.deleteError = ApiException(ApiError.CONFLICT, 409, ProblemTypes.LAST_ADMIN)
        val vm = askDelete()
        vm.confirmDelete()
        assertEquals(ConnectError.LAST_ADMIN, vm.state.value.error)
        assertNull(vm.state.value.deleteForm)
        assertTrue(deleteWipes.isEmpty())
        assertFalse(ServerSessionEvents.leaving)
        assertFalse(ServerSessionEvents.revoked.value)
    }

    @Test
    fun aLockedAccountIsRateLimitedAndKeepsTheForm() {
        api.deleteError = ApiException(ApiError.RATE_LIMITED, 429, ProblemTypes.RATE_LIMITED)
        val vm = askDelete()
        vm.confirmDelete()
        assertEquals(ConnectError.RATE_LIMITED, vm.state.value.error)
        assertEquals(DeletePhase.CONFIRM, vm.state.value.deleteForm?.phase)
        assertEquals("secret password", vm.state.value.deleteForm?.password)
        assertTrue(deleteWipes.isEmpty())
    }

    @Test
    fun offlineNothingChanges() {
        api.deleteError = ApiException(ApiError.NETWORK)
        val vm = askDelete()
        vm.confirmDelete()
        assertEquals(ConnectError.NETWORK, vm.state.value.error)
        assertEquals(DeletePhase.CONFIRM, vm.state.value.deleteForm?.phase)
        assertTrue(deleteWipes.isEmpty())
        assertFalse(ServerSessionEvents.leaving)
    }

    @Test
    fun aGuestSendsNoPassword() {
        val vm = askDelete(guest = true)
        vm.confirmDelete()
        assertEquals(listOf<String?>(null), api.deletes)
        assertEquals(listOf(false), deleteWipes)
    }

    @Test
    fun aGuestPasswordTypedSomehowIsStillNotSent() {
        val vm = vm()
        vm.askDelete(guest = true)
        vm.deletePasswordChanged("ignored")
        vm.confirmDelete()
        assertEquals(listOf<String?>(null), api.deletes)
    }

    @Test
    fun syncFailedTheUserCanCancel() {
        pending = 2
        leftAfterSync = 2
        val vm = askDelete()
        vm.confirmDelete()
        val form = vm.state.value.deleteForm!!
        assertEquals(DeletePhase.SYNC_FAILED, form.phase)
        assertEquals(2, form.pending)
        assertTrue(api.deletes.isEmpty())
        vm.cancelDelete()
        assertNull(vm.state.value.deleteForm)
        assertTrue(deleteWipes.isEmpty())
    }

    @Test
    fun syncFailedThenDeleteAnywaySavesTheUnsentChangesFirst() {
        pending = 2
        leftAfterSync = 1
        val vm = askDelete()
        vm.confirmDelete()
        assertEquals(DeletePhase.SYNC_FAILED, vm.state.value.deleteForm?.phase)
        vm.deleteAnyway()
        // The password typed before the sync is kept
        assertEquals(listOf<String?>("secret password"), api.deletes)
        assertEquals(listOf(true), deleteWipes)
        assertEquals(DeletePhase.DONE, vm.state.value.deleteForm?.phase)
    }

    @Test
    fun aSyncThatThrowsAlsoLetsTheUserChoose() {
        pending = 1
        val vm = AccountViewModel(
            api = api,
            pendingChanges = { pending },
            syncFirst = { throw ApiException(ApiError.NETWORK) },
            onMe = {},
            onPromoted = {},
            wipe = { wipes++ },
            wipeAfterDelete = { deleteWipes += it },
        )
        vm.askDelete(guest = true)
        vm.confirmDelete()
        assertEquals(DeletePhase.SYNC_FAILED, vm.state.value.deleteForm?.phase)
        assertEquals(1, vm.state.value.deleteForm?.pending)
        vm.deleteAnyway()
        assertEquals(listOf(true), deleteWipes)
    }

    @Test
    fun aRevokeReportedDuringTheDeleteDoesNotWipeASecondTime() {
        // Another call answered 401 right after the delete: the client reports the session revoked
        api.afterDelete = { ServerSessionEvents.sessionRevoked() }
        val vm = askDelete()
        vm.confirmDelete()
        assertEquals(listOf(false), deleteWipes)
        assertFalse(ServerSessionEvents.revoked.value, "the revoke handler would wipe again")
        assertEquals(0, wipes)
        assertEquals(DeletePhase.DONE, vm.state.value.deleteForm?.phase)
    }

    @Test
    fun aRevokedSessionDuringTheDeleteIsHandedToTheRevokeHandler() {
        api.deleteError = ApiException(ApiError.SESSION_REVOKED, 401, ProblemTypes.SESSION_REVOKED)
        val vm = askDelete()
        vm.confirmDelete()
        assertTrue(deleteWipes.isEmpty())
        assertNull(vm.state.value.deleteForm)
        assertTrue(ServerSessionEvents.revoked.value)
        assertFalse(ServerSessionEvents.leaving)
    }

    @Test
    fun aFailedWipeAfterTheDeleteIsRetriedByTheRevokeHandler() {
        deleteWipeError = IllegalStateException("disk")
        val vm = askDelete()
        vm.confirmDelete()
        assertEquals(1, deleteWipes.size)
        assertNull(vm.state.value.deleteForm)
        assertTrue(ServerSessionEvents.revoked.value)
        assertFalse(ServerSessionEvents.leaving)
    }

    @Test
    fun aRunningDeleteCannotBeCancelled() {
        pending = 1
        val gate = kotlinx.coroutines.CompletableDeferred<Int>()
        val vm = AccountViewModel(
            api = api,
            pendingChanges = { pending },
            syncFirst = { gate.await() },
            onMe = {},
            onPromoted = {},
            wipe = {},
            wipeAfterDelete = { deleteWipes += it },
        )
        vm.askDelete(guest = true)
        vm.confirmDelete()
        assertEquals(DeletePhase.SYNCING, vm.state.value.deleteForm?.phase)
        vm.cancelDelete()
        assertNotNull(vm.state.value.deleteForm)
        gate.complete(0)
        assertEquals(listOf(false), deleteWipes)
    }
}
