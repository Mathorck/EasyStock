package ch.mathorck.easystock.util

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching] for coroutine code: a [CancellationException] is rethrown so the coroutine stops,
 * other exceptions become a failure.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
