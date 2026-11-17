package ch.mathorck.easystock.viewmodels.backup

import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.enums.backup.ImportMode
import ch.mathorck.easystock.services.backup.FakeBackupService
import ch.mathorck.easystock.services.backup.ImportInput
import ch.mathorck.easystock.services.backup.WifiState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private lateinit var service: FakeBackupService
    private lateinit var viewModel: BackupViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        service = FakeBackupService()
        viewModel = BackupViewModel(service)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun openPreview() {
        viewModel.fileOpened(ImportInput.File("/tmp/in"))
        assertIs<BackupStep.Preview>(viewModel.state.value.step)
    }

    @Test
    fun leavingAfterAnImportResetsTheResult() {
        openPreview()
        viewModel.import(ImportMode.MERGE)
        assertIs<BackupStep.Done>(viewModel.state.value.step)

        viewModel.leave()

        assertEquals(BackupUiState(), viewModel.state.value)
    }

    @Test
    fun leavingAfterAnExportResetsAndDeletesTheFile() {
        viewModel.export("")
        assertIs<BackupStep.Exported>(viewModel.state.value.step)

        viewModel.leave()

        assertEquals(BackupUiState(), viewModel.state.value)
        assertEquals(1, service.discards)
    }

    @Test
    fun leavingWhileSharingOverWifiKeepsTheShare() {
        viewModel.export("")
        viewModel.startWifi()

        viewModel.leave()

        assertIs<BackupStep.SendingWifi>(viewModel.state.value.step)
        assertFalse(service.sender.stopped)
        assertEquals(0, service.discards)

        viewModel.enter()
        assertIs<BackupStep.SendingWifi>(viewModel.state.value.step)
    }

    @Test
    fun aShareThatEndsWhileAwayShowsTheStartOnTheNextVisit() {
        viewModel.export("")
        viewModel.startWifi()
        viewModel.leave()

        service.sender.flow.value = WifiState.Expired

        assertEquals(BackupUiState(), viewModel.state.value)
        assertEquals(1, service.discards)
        viewModel.enter()
        assertEquals(BackupUiState(), viewModel.state.value)
    }

    @Test
    fun aShareThatEndsWhileOnScreenShowsItsResult() {
        viewModel.export("")
        viewModel.startWifi()

        service.sender.flow.value = WifiState.Done

        val step = viewModel.state.value.step
        assertIs<BackupStep.SendingWifi>(step)
        assertEquals(WifiState.Done, step.state)
    }

    @Test
    fun aShareThatEndedOnScreenIsResetWhenLeaving() {
        viewModel.export("")
        viewModel.startWifi()
        service.sender.flow.value = WifiState.Done

        viewModel.leave()

        assertEquals(BackupUiState(), viewModel.state.value)
    }

    @Test
    fun comingBackBeforeTheEndShowsTheShareThenItEndsNormally() {
        viewModel.export("")
        viewModel.startWifi()
        viewModel.leave()
        viewModel.enter()

        service.sender.flow.value = WifiState.Expired

        assertEquals(WifiState.Expired, (viewModel.state.value.step as BackupStep.SendingWifi).state)
    }

    @Test
    fun stoppingAShareKeptWhileAwayStopsIt() {
        viewModel.export("")
        viewModel.startWifi()
        viewModel.leave()

        viewModel.reset()

        assertTrue(service.sender.stopped)
        assertEquals(BackupUiState(), viewModel.state.value)
    }

    @Test
    fun leavingAfterAnErrorResetsIt() {
        openPreview()
        service.failImport = true
        viewModel.import(ImportMode.REPLACE)
        assertEquals(BackupError.FAILED, viewModel.state.value.error)

        viewModel.leave()

        assertNull(viewModel.state.value.error)
        assertEquals(BackupStep.Home, viewModel.state.value.step)
    }

    @Test
    fun leavingDuringAnImportLetsItFinishThenShowsTheStart() {
        openPreview()
        val gate = CompletableDeferred<Unit>()
        service.importGate = gate
        viewModel.import(ImportMode.MERGE)
        assertEquals(BackupStep.Working, viewModel.state.value.step)

        viewModel.leave()
        assertEquals(BackupStep.Working, viewModel.state.value.step)

        gate.complete(Unit)
        assertEquals(BackupUiState(), viewModel.state.value)
    }

    @Test
    fun anImportAfterALeaveShowsItsResult() {
        openPreview()
        viewModel.import(ImportMode.MERGE)
        viewModel.leave()

        openPreview()
        viewModel.import(ImportMode.MERGE)

        assertIs<BackupStep.Done>(viewModel.state.value.step)
    }

    /** A server code scanned in the backup screen goes to the connect screen, nothing is reported. */
    @Test
    fun aServerCodeIsHandedOver() {
        viewModel.scan()
        viewModel.scanned("easystock://invite?server=https://nas.example&token=abc")

        val state = viewModel.state.value
        assertEquals(ch.mathorck.easystock.services.server.JoinCode("https://nas.example", ch.mathorck.easystock.enums.server.JoinKind.INVITE, "abc"), state.joinCode)
        assertNull(state.error)
        assertEquals(BackupStep.Home, state.step)

        viewModel.joinCodeHandled()
        assertNull(viewModel.state.value.joinCode)
    }
}
