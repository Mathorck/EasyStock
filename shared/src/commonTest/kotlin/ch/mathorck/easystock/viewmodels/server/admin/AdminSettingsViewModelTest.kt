package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.data.dto.server.AdminSettings
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.server.FakeAdminApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AdminSettingsViewModelTest {
    private val api = FakeAdminApi()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun serverNameIsSaved() {
        api.settings = AdminSettings(serverName = "Atelier")
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        vm.setServerName(" Maison ")
        assertEquals("Maison", api.settings.serverName)
        assertEquals("Maison", vm.state.value.settings.data?.serverName)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun blankNameIsRejected() {
        api.settings = AdminSettings(serverName = "Atelier")
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        assertFalse(vm.state.value.canSaveServerName("   "))
        vm.setServerName("   ")
        vm.setServerName("")
        assertEquals(0, api.settingsWrites)
        assertEquals("Atelier", api.settings.serverName)
    }

    @Test
    fun sameNameIsNotSaved() {
        api.settings = AdminSettings(serverName = "Atelier")
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        assertFalse(vm.state.value.canSaveServerName(" Atelier "))
        vm.setServerName(" Atelier ")
        assertEquals(0, api.settingsWrites)
    }

    @Test
    fun nameRule() {
        assertTrue(isNewServerName(" Maison ", "Atelier"))
        assertFalse(isNewServerName("Atelier", "Atelier"))
        assertFalse(isNewServerName(" \t ", "Atelier"))
        // Nothing loaded yet, or a save running: no save
        assertFalse(AdminSettingsState().canSaveServerName("Maison"))
        val loaded = AdminSettingsState(Loaded(AdminSettings("Atelier"), loading = false))
        assertTrue(loaded.canSaveServerName("Maison"))
        assertFalse(loaded.copy(saving = true).canSaveServerName("Maison"))
    }

    @Test
    fun blankServerAnswerKeepsTheSentName() {
        api.settings = AdminSettings(serverName = "Atelier")
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        api.settingsAnswer = AdminSettings()
        vm.setServerName("Maison")
        assertEquals("Maison", vm.state.value.settings.data?.serverName)
    }

    @Test
    fun loadRunningDuringSaveDoesNotBringTheOldNameBack() {
        api.settings = AdminSettings(serverName = "Atelier")
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        val gate = CompletableDeferred<Unit>()
        api.settingsGate = gate
        vm.refresh() // reads "Atelier", then waits
        assertTrue(vm.state.value.settings.refreshing)
        api.settingsGate = null
        vm.setServerName("Maison")
        gate.complete(Unit)
        assertEquals("Maison", vm.state.value.settings.data?.serverName)
        assertFalse(vm.state.value.settings.refreshing)
    }

    @Test
    fun failedSaveStopsTheCancelledRefresh() {
        api.settings = AdminSettings(serverName = "Atelier")
        val vm = AdminSettingsViewModel(api)
        vm.refresh()
        val gate = CompletableDeferred<Unit>()
        api.settingsGate = gate
        vm.refresh()
        api.fail = ApiException(ApiError.SERVER)
        vm.setServerName("Maison")
        gate.complete(Unit)
        val state = vm.state.value
        assertNotNull(state.failure)
        assertFalse(state.saving)
        assertFalse(state.settings.refreshing)
        assertEquals("Atelier", state.settings.data?.serverName)
    }
}
