package ch.mathorck.easystock.viewmodels.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.server.LocalFile
import ch.mathorck.easystock.services.server.MyDataApi
import ch.mathorck.easystock.services.server.ProblemTypes
import ch.mathorck.easystock.services.server.ServerFileStore
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Download my data" (right of access): `GET /me/data` streamed to a file in the server cache folder
 * (`cache/server/share`, erased by the wipe), then saved or shared with the system pickers. The copy in the
 * cache is dropped once saved, when the dialog is closed, and when the view model goes.
 */
class MyDataViewModel(
    private val api: MyDataApi,
    private val files: ServerFileStore,
    private val fileName: () -> String = { myDataFileName() },
    /** Runs the cache cleanup once the screen is gone ([viewModelScope] is cancelled by then). */
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    private val _state = MutableStateFlow(MyDataState())
    val state: StateFlow<MyDataState> = _state.asStateFlow()

    fun open() = _state.update { if (it.downloading) it else MyDataState(summary = true) }

    /** Closes the dialog; a file not saved leaves the cache (not right after a share: see [onCleared]). */
    fun close() {
        if (_state.value.downloading) return
        val had = _state.value.downloaded != null
        _state.value = MyDataState()
        if (had) viewModelScope.launch { clearFiles() }
    }

    fun download() {
        val current = _state.value
        if (current.downloading || !current.summary) return
        _state.update { it.copy(downloading = true, error = null, saved = false) }
        viewModelScope.launch {
            try {
                var file: LocalFile? = null
                api.downloadMyData { channel -> file = files.receive(fileName(), MIME, channel) }
                _state.update { it.copy(downloading = false, downloaded = file) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A file half written stays out of reach: drop it
                clearFiles()
                _state.update { it.copy(downloading = false, downloaded = null, error = myDataErrorOf(e)) }
            }
        }
    }

    /** Saved where the user chose: the copy in the cache is not needed any more. */
    fun saved() {
        _state.update { it.copy(downloaded = null, saved = true) }
        viewModelScope.launch { clearFiles() }
    }

    fun fileFailed() = _state.update { it.copy(error = MyDataError.FILE) }

    fun consumeError() = _state.update { it.copy(error = null) }

    /** The screen is closed: the export leaves the cache. */
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

    companion object {
        const val MIME = "application/json"
    }
}

internal fun myDataErrorOf(e: Throwable): MyDataError {
    val api = e as? ApiException ?: return MyDataError.FILE
    if (api.problemType == ProblemTypes.RATE_LIMITED) return MyDataError.RATE_LIMITED
    return when (api.error) {
        ApiError.NETWORK -> MyDataError.NETWORK
        ApiError.RATE_LIMITED -> MyDataError.RATE_LIMITED
        ApiError.SESSION_REVOKED -> MyDataError.SESSION_REVOKED
        ApiError.APP_TOO_OLD -> MyDataError.APP_TOO_OLD
        else -> MyDataError.SERVER
    }
}

/** `easystock-my-data-2026-11-14.json`, the UTC date, like the server's file name. */
fun myDataFileName(now: Instant = Clock.System.now()): String = "easystock-my-data-${now.toString().take(10)}.json"
