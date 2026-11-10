package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.backup.BackupError
import ch.mathorck.easystock.backup.BackupException
import ch.mathorck.easystock.backup.BackupManifest
import ch.mathorck.easystock.backup.BackupService
import ch.mathorck.easystock.backup.ExportedBackup
import ch.mathorck.easystock.backup.ImportInput
import ch.mathorck.easystock.backup.ImportMode
import ch.mathorck.easystock.backup.ImportSummary
import ch.mathorck.easystock.backup.OpenedBackup
import ch.mathorck.easystock.backup.QrDecision
import ch.mathorck.easystock.backup.WifiSender
import ch.mathorck.easystock.backup.WifiState
import ch.mathorck.easystock.backup.WifiTicket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes

private class FakeSender : WifiSender {
    override val ticket = WifiTicket(host = "192.168.1.2", port = 8080, token = "t", key = "k")
    override val timeout = 5.minutes
    val flow = MutableStateFlow<WifiState>(WifiState.Waiting)
    override val state: StateFlow<WifiState> = flow
    var stopped = false

    override fun stop() {
        stopped = true
    }
}

private class FakeBackupService : BackupService {
    val sender = FakeSender()
    var failImport = false

    /** When set, [import] waits for it. */
    var importGate: CompletableDeferred<Unit>? = null
    var discards = 0

    private val manifest = BackupManifest(appVersion = "test", createdAt = "2026-11-10T00:00:00Z")
    private val summary = ImportSummary(ImportMode.MERGE, emptyMap(), emptyMap(), 0, emptyList())

    override suspend fun export(password: CharArray?) =
        ExportedBackup("/tmp/b", "b.zip", 1, manifest, 0, false, QrDecision.TooLarge)

    override suspend fun startWifi(export: ExportedBackup): WifiSender = sender

    override suspend fun download(ticket: WifiTicket, onProgress: (Long) -> Unit): ImportInput.File =
        ImportInput.File("/tmp/d")

    override suspend fun needsPassword(input: ImportInput) = false

    override suspend fun open(input: ImportInput, password: CharArray?) =
        OpenedBackup(manifest, BackupData(), 0, null)

    override suspend fun import(backup: OpenedBackup, mode: ImportMode): ImportSummary {
        importGate?.await()
        if (failImport) throw BackupException(BackupError.FAILED)
        return summary
    }

    override fun discard(export: ExportedBackup) {
        discards++
    }

    override fun discard(input: ImportInput) {
        discards++
    }

    override fun discard(backup: OpenedBackup) {
        discards++
    }
}

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
}
