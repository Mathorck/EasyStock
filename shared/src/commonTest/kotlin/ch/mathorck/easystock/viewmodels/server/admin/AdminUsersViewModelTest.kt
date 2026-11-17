package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.server.FakeAdminApi
import ch.mathorck.easystock.services.server.ProblemTypes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

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
        assertEquals(Triple("g1", Role.VIEWER, "AB-CDE"), api.promoted)
        assertNull(vm.state.value.promote)
        assertEquals(UserMessage.PROMOTION_PENDING, vm.state.value.message)
    }

    @Test
    fun renameSendsTheTrimmedNameAndReloads() {
        vm.refresh()
        val reads = api.userReads
        vm.rename("a", "  Anna  ")
        assertEquals("a" to "Anna", api.renamed)
        assertEquals(UserMessage.RENAMED, vm.state.value.message)
        assertEquals(reads + 1, api.userReads)
    }

    @Test
    fun renameWithAnInvalidNameSendsNothing() {
        vm.refresh()
        vm.rename("a", "   ")
        vm.rename("a", "x".repeat(51))
        assertNull(api.renamed)
        assertNull(vm.state.value.message)
    }

    @Test
    fun aFailedRenameShowsTheProblem() {
        vm.refresh()
        api.fail = ApiException(ApiError.INVALID, 400, "validation-failed")
        vm.rename("a", "Anna")
        assertEquals(ApiError.INVALID, vm.state.value.failure?.error)
        assertNull(vm.state.value.message)
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
