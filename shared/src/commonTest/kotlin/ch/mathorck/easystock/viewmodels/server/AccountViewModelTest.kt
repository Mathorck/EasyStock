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
    val promotions = mutableListOf<CompletePromotionRequest>()
    val promoted = TokenAnswer("a2", "r2", user = SessionUser("u1", "Ann", "member", "human"))

    override suspend fun me(): MeAnswer {
        meError?.let { throw it }
        return me
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
    private var leavingDuringLogout = false

    private fun vm() = AccountViewModel(
        api = api,
        pendingChanges = { pending },
        onMe = { stored += it },
        onPromoted = { promotedWith += it },
        wipe = {
            leavingDuringLogout = ServerSessionEvents.leaving
            wipes++
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
}
