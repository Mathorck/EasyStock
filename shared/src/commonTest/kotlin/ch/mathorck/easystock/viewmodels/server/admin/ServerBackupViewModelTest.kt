package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.server.ServerImportMode
import ch.mathorck.easystock.services.server.FakeAdminApi
import ch.mathorck.easystock.services.server.FakeFileStore
import ch.mathorck.easystock.services.server.LocalFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ServerBackupViewModelTest {
    private val api = FakeAdminApi()
    private val files = FakeFileStore()
    private lateinit var vm: ServerBackupViewModel
    private val file = LocalFile("/c/b", "b.easystock", 10, ServerBackupViewModel.MIME)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ServerBackupViewModel(api, files, fileName = { "backup.easystock" })
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
    fun theCacheIsClearedAfterImportSaveAndClose() {
        vm.picked(file)
        vm.import("REPLACE")
        assertEquals(1, files.clears)

        vm.download()
        assertNotNull(vm.state.value.downloaded)
        vm.saved()
        assertNull(vm.state.value.downloaded)
        assertEquals(2, files.clears)

        val store = androidx.lifecycle.ViewModelStore()
        val closing = ServerBackupViewModel(api, files, { "b.easystock" }, cleanupScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        store.put("backup", closing)
        store.clear()
        assertEquals(3, files.clears)
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
