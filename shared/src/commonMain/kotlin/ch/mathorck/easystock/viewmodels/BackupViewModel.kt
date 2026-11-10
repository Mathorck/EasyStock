package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.backup.BackupError
import ch.mathorck.easystock.backup.BackupException
import ch.mathorck.easystock.backup.BackupService
import ch.mathorck.easystock.backup.ExportedBackup
import ch.mathorck.easystock.backup.ImportInput
import ch.mathorck.easystock.backup.ImportMode
import ch.mathorck.easystock.backup.ImportSummary
import ch.mathorck.easystock.backup.OpenedBackup
import ch.mathorck.easystock.backup.QrDecision
import ch.mathorck.easystock.backup.QrTransfer
import ch.mathorck.easystock.backup.ScannedQr
import ch.mathorck.easystock.backup.WifiSender
import ch.mathorck.easystock.backup.WifiState
import ch.mathorck.easystock.backup.WifiTicket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the backup screen is. */
sealed interface BackupStep {
    data object Home : BackupStep

    data object Exporting : BackupStep

    /** The backup is built: choose how to send it. */
    data class Exported(val export: ExportedBackup) : BackupStep

    data class ShowingQr(val export: ExportedBackup, val text: String) : BackupStep

    data class SendingWifi(val export: ExportedBackup, val qrText: String, val state: WifiState) : BackupStep

    data object Scanning : BackupStep

    data class Downloading(val receivedBytes: Long) : BackupStep

    /** [wrong]: the last password did not open it. */
    data class PasswordNeeded(val wrong: Boolean = false) : BackupStep

    /** Opening or importing. */
    data object Working : BackupStep

    /** Read and checked: choose merge or replace. */
    data class Preview(val backup: OpenedBackup) : BackupStep

    data class Done(val summary: ImportSummary) : BackupStep
}

data class BackupUiState(
    val step: BackupStep = BackupStep.Home,
    /** Shown in a dialog, the step stays. */
    val error: BackupError? = null,
)

/**
 * Settings › Backup. Holds the temporary files of the current export or import and deletes them
 * when leaving the screen. Never logs: rows and keys stay in memory.
 */
class BackupViewModel(private val service: BackupService) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var export: ExportedBackup? = null
    private var input: ImportInput? = null
    private var opened: OpenedBackup? = null
    private var wifi: WifiSender? = null
    private var job: Job? = null

    private fun go(step: BackupStep) = _state.update { it.copy(step = step) }

    private fun fail(error: BackupError, step: BackupStep = _state.value.step) =
        _state.update { BackupUiState(step = step, error = error) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** A failure outside the view model (file picker, share sheet): shown, the step stays. */
    fun reportError(error: BackupError) = _state.update { it.copy(error = error) }

    private fun run(onError: (BackupError) -> Unit, block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                onError(e.error)
            } catch (e: Exception) {
                onError(BackupError.FAILED)
            }
        }
    }

    // ---------- Send ----------

    /** Builds the backup, protected when [password] is not empty. */
    fun export(password: String) {
        clearExport()
        go(BackupStep.Exporting)
        run(onError = { fail(it, BackupStep.Home) }) {
            val chars = password.toCharArray()
            val built = try {
                service.export(chars.takeIf { it.isNotEmpty() })
            } finally {
                chars.fill('\u0000')
            }
            export = built
            go(BackupStep.Exported(built))
        }
    }

    fun showQr() {
        val built = export ?: return
        val qr = built.qr as? QrDecision.Fits ?: return
        go(BackupStep.ShowingQr(built, qr.text))
    }

    fun startWifi() {
        val built = export ?: return
        stopWifi()
        run(onError = { fail(it, BackupStep.Exported(built)) }) {
            val sender = service.startWifi(built)
            wifi = sender
            val text = QrTransfer.wifiQrText(sender.ticket)
            sender.state.collect { state -> go(BackupStep.SendingWifi(built, text, state)) }
        }
    }

    /** Back to the send options of the current backup. */
    fun backToExport() {
        stopWifi()
        export?.let { go(BackupStep.Exported(it)) } ?: go(BackupStep.Home)
    }

    private fun stopWifi() {
        wifi?.stop()
        wifi = null
        job?.cancel()
    }

    private fun clearExport() {
        stopWifi()
        export?.let(service::discard)
        export = null
    }

    // ---------- Receive ----------

    fun scan() {
        clearImport()
        go(BackupStep.Scanning)
    }

    /** A QR code was read. Anything else than an EasyStock code is reported, scanning stops. */
    fun scanned(text: String) {
        val scanned = try {
            QrTransfer.parse(text)
        } catch (e: BackupException) {
            fail(e.error, BackupStep.Home)
            return
        }
        when (scanned) {
            is ScannedQr.Data -> received(ImportInput.Qr(scanned.payload))
            is ScannedQr.Wifi -> download(scanned.ticket)
        }
    }

    private fun download(ticket: WifiTicket) {
        go(BackupStep.Downloading(0))
        run(onError = { fail(it, BackupStep.Home) }) {
            val file = service.download(ticket) { received -> go(BackupStep.Downloading(received)) }
            received(file)
        }
    }

    /** A file picked by the user, already copied to the app cache. */
    fun fileOpened(file: ImportInput.File) {
        clearImport()
        received(file)
    }

    private fun received(received: ImportInput) {
        input = received
        go(BackupStep.Working)
        run(onError = { fail(it, BackupStep.Home) }) {
            if (service.needsPassword(received)) go(BackupStep.PasswordNeeded()) else open(null)
        }
    }

    fun submitPassword(password: String) {
        if (input == null) return
        go(BackupStep.Working)
        run(onError = { error ->
            when (error) {
                BackupError.WRONG_PASSWORD, BackupError.PASSWORD_REQUIRED -> go(BackupStep.PasswordNeeded(wrong = true))
                else -> fail(error, BackupStep.Home)
            }
        }) {
            val chars = password.toCharArray()
            try {
                open(chars)
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    private suspend fun open(password: CharArray?) {
        val current = input ?: return
        val backup = service.open(current, password)
        opened = backup
        go(BackupStep.Preview(backup))
    }

    /** Writes the backup. [ImportMode.REPLACE] only after the user confirmed it. */
    fun import(mode: ImportMode) {
        val backup = opened ?: return
        go(BackupStep.Working)
        run(onError = { fail(it, BackupStep.Preview(backup)) }) {
            val summary = service.import(backup, mode)
            clearImport()
            go(BackupStep.Done(summary))
        }
    }

    private fun clearImport() {
        opened?.let(service::discard)
        opened = null
        input?.let(service::discard)
        input = null
    }

    /** Back to the start, every temporary file deleted. */
    fun reset() {
        job?.cancel()
        clearExport()
        clearImport()
        _state.value = BackupUiState()
    }

    override fun onCleared() {
        reset()
    }
}
