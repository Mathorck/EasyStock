package ch.mathorck.easystock.viewmodels.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.reset.matchesConfirmation
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.LocalFile
import ch.mathorck.easystock.server.ServerFileStore
import ch.mathorck.easystock.server.ServerImportMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The import being prepared: a picked file and how to load it. */
data class ServerImportForm(
    val file: LocalFile,
    val mode: ServerImportMode = ServerImportMode.MERGE,
    /** What the Admin typed to confirm a replace. */
    val typed: String = "",
)

/** True when the import can be sent: merge right away, replace only with the [word] typed. */
fun ServerImportForm.canImport(word: String): Boolean =
    mode == ServerImportMode.MERGE || matchesConfirmation(typed, word)

enum class ServerBackupMessage { DOWNLOADED, IMPORTED, SAVED }

data class ServerBackupState(
    val downloading: Boolean = false,
    /** The last downloaded backup, to save or share. */
    val downloaded: LocalFile? = null,
    val import: ServerImportForm? = null,
    val importing: Boolean = false,
    val failure: ActionFailure? = null,
    /** A file could not be written, read or picked. */
    val fileFailed: Boolean = false,
    val message: ServerBackupMessage? = null,
) {
    val busy: Boolean get() = downloading || importing
}

/** Server backup: download it to a file, and import a file into the server. */
class ServerBackupViewModel(
    private val api: AdminApi,
    private val files: ServerFileStore,
    /** File name of a download, e.g. `easystock-server-2026-11-11.esbk`. */
    private val fileName: () -> String,
    /** Runs the cache cleanup once the screen is gone ([viewModelScope] is cancelled by then). */
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    private val _state = MutableStateFlow(ServerBackupState())
    val state: StateFlow<ServerBackupState> = _state.asStateFlow()

    fun download() {
        if (_state.value.busy) return
        _state.update { it.copy(downloading = true, failure = null, fileFailed = false, message = null) }
        viewModelScope.launch {
            try {
                var file: LocalFile? = null
                api.downloadBackup { channel -> file = files.receive(fileName(), MIME, channel) }
                _state.update { it.copy(downloading = false, downloaded = file, message = ServerBackupMessage.DOWNLOADED) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, failure = e.toFailure()) }
            }
        }
    }

    /** A file was picked: the Admin chooses merge or replace. */
    // The cache keeps one file at a time: the picked file replaced the downloaded one
    fun picked(file: LocalFile) = _state.update { it.copy(import = ServerImportForm(file), downloaded = null, message = null, failure = null) }

    fun chooseMode(mode: ServerImportMode) = _state.update { it.copy(import = it.import?.copy(mode = mode, typed = "")) }

    fun typeConfirmation(text: String) = _state.update { it.copy(import = it.import?.copy(typed = text)) }

    fun closeImport() = _state.update { it.copy(import = null) }

    /** Sends the picked file. A replace needs the localized [word] typed (see [matchesConfirmation]). */
    fun import(word: String) {
        val form = _state.value.import ?: return
        if (_state.value.busy || !form.canImport(word)) return
        _state.update { it.copy(importing = true, failure = null, message = null) }
        viewModelScope.launch {
            try {
                api.importBackup(form.mode, form.file.size) { files.read(form.file) }
                _state.update { it.copy(importing = false, import = null, message = ServerBackupMessage.IMPORTED) }
                // The server has it: the copy of the backup leaves the phone cache
                clearFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(importing = false, failure = e.toFailure()) }
            }
        }
    }

    /** Saved where the Admin chose: the copy in the cache is not needed any more. */
    fun saved() {
        _state.update { it.copy(downloaded = null, message = ServerBackupMessage.SAVED) }
        viewModelScope.launch { clearFiles() }
    }

    /**
     * The screen is closed: the full server backup and a picked file leave the cache. Not right after a
     * share: the receiving app reads the file later.
     */
    override fun onCleared() {
        cleanupScope.launch { clearFiles() }
    }

    private suspend fun clearFiles() {
        try {
            files.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Cache only: a reset wipes it anyway
        }
    }

    fun fileFailed() = _state.update { it.copy(fileFailed = true) }

    fun consumeMessage() = _state.update { it.copy(message = null, failure = null, fileFailed = false) }

    companion object {
        const val MIME = "application/octet-stream"
    }
}
