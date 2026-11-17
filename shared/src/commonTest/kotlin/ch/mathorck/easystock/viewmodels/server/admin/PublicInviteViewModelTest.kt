package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.data.dto.server.AdminSettings
import ch.mathorck.easystock.services.server.FakeAdminApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class PublicInviteViewModelTest {
    private val api = FakeAdminApi()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun refreshReloadsTheServerNameAfterARename() {
        api.settings = AdminSettings(serverName = "Atelier")
        val vm = PublicInviteViewModel(api)
        assertEquals("Atelier", vm.state.value.serverName)
        api.settings = AdminSettings(serverName = "Garage")
        vm.refresh()
        assertEquals("Garage", vm.state.value.serverName)
    }
}
