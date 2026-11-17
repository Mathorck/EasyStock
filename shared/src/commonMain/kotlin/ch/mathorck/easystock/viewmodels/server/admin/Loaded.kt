package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.server.ApiError

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
