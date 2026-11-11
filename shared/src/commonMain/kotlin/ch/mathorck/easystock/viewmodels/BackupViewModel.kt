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
import ch.mathorck.easystock.server.JoinCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/** Where the backup screen is. */
sealed interface BackupStep {
    data object Home : BackupStep

    data object Exporting : BackupStep

    /** The backup is built: choose how to send it. */
    data class Exported(val export: ExportedBackup) : BackupStep

    data class ShowingQr(val export: ExportedBackup, val text: String) : BackupStep

    data class SendingWifi(
        val export: ExportedBackup,
        val qrText: String,
        val state: WifiState,
        /** When the code stops being valid. */
        val deadline: ComparableTimeMark,
    ) : BackupStep

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
    /** A server join code was scanned: the screen hands it to Settings › Server, then [BackupViewModel.joinCodeHandled]. */
    val joinCode: JoinCode? = null,
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

    /** True while the import is being written: it cannot be stopped half way. */
    private var writing = false

    /** The screen was left while the import was being written: its result is not shown later. */
    private var left = false

    /** The screen was left while a Wi-Fi share runs: when it ends, the next visit shows the start. */
    private var awayDuringWifi = false

    private fun go(step: BackupStep) = _state.update { it.copy(step = step) }

    private fun fail(error: BackupError, step: BackupStep = _state.value.step) =
        _state.update { BackupUiState(step = step, error = error) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun joinCodeHandled() = _state.update { it.copy(joinCode = null) }

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
            val deadline = TimeSource.Monotonic.markNow() + sender.timeout
            sender.state.collect { state ->
                if (awayDuringWifi && !state.isActive()) {
                    // Ended while nobody looks: the next visit shows the start, not a stale result
                    reset()
                } else {
                    go(BackupStep.SendingWifi(built, text, state, deadline))
                }
            }
        }
    }

    /** Back to the send options of the current backup. */
    fun backToExport() {
        stopWifi()
        export?.let { go(BackupStep.Exported(it)) } ?: go(BackupStep.Home)
    }

    private fun WifiState.isActive() = this is WifiState.Waiting || this is WifiState.Sending

    private fun stopWifi() {
        awayDuringWifi = false
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
            // Not a backup: the connect screen takes it
            is ScannedQr.Join -> _state.value = BackupUiState(step = BackupStep.Home, joinCode = scanned.code)
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
        writing = true
        left = false
        run(onError = { writing = false; fail(it, BackupStep.Preview(backup)) }) {
            val summary = service.import(backup, mode)
            writing = false
            clearImport()
            go(if (left) BackupStep.Home else BackupStep.Done(summary))
            left = false
        }
    }

    private fun clearImport() {
        opened?.let(service::discard)
        opened = null
        input?.let(service::discard)
        input = null
    }

    /** The screen is shown (again): a Wi-Fi share that kept running is shown as it is. */
    fun enter() {
        awayDuringWifi = false
    }

    /**
     * The screen is gone (back, other page, leaving Settings): a result or error must not wait for
     * the user's return. Everything resets, including a download. Two things continue: an import
     * being written (it cannot be stopped; when it ends the screen is back at its start instead of
     * showing the result), and a Wi-Fi share (it runs in the sender's own scope and ends by itself,
     * after its timeout, one download or [reset]; when it ends while away, the start is shown).
     */
    fun leave() {
        if (writing) {
            left = true
            return
        }
        val step = _state.value.step
        if (step is BackupStep.SendingWifi && step.state.isActive()) {
            awayDuringWifi = true
            return
        }
        reset()
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
