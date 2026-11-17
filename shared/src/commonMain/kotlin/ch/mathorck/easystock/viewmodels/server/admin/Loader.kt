package ch.mathorck.easystock.viewmodels.server.admin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
