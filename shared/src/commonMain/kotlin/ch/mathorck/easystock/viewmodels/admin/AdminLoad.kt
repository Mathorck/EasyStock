package ch.mathorck.easystock.viewmodels.admin

import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data read from the server. [data] is kept while a refresh runs or fails, so the screen keeps the list
 * and shows the error over it.
 */
data class Loaded<T>(
    val data: T? = null,
    /** First load, nothing to show yet. */
    val loading: Boolean = true,
    /** Pull-to-refresh (or reload after an action) with [data] shown. */
    val refreshing: Boolean = false,
    /** Last load failed. */
    val error: ApiError? = null,
) {
    /** Loaded, with nothing in it. */
    fun isEmpty(empty: (T) -> Boolean): Boolean = data != null && empty(data)
}

/** The error of a failed action (not of the load), shown once as a message. */
data class ActionFailure(val error: ApiError, val problemType: String? = null)

internal fun Throwable.toFailure(): ActionFailure =
    (this as? ApiException)?.let { ActionFailure(it.error, it.problemType) } ?: ActionFailure(ApiError.SERVER)

/** Loads into [state] with [read], keeping the old data while it runs. Cancels a previous load. */
internal class Loader<T>(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<Loaded<T>>,
    private val read: suspend () -> T,
) {
    private var job: Job? = null

    fun load(refresh: Boolean = false) {
        job?.cancel()
        state.update {
            if (it.data == null) it.copy(loading = true, refreshing = false, error = null)
            else it.copy(refreshing = refresh, loading = false, error = null)
        }
        job = scope.launch {
            try {
                val data = read()
                state.value = Loaded(data, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(loading = false, refreshing = false, error = e.toFailure().error) }
            }
        }
    }

    /** Stops a running load: its result is dropped. The data shown stays. */
    fun cancel() {
        job?.cancel()
        job = null
        state.update { it.copy(refreshing = false) }
    }
}
