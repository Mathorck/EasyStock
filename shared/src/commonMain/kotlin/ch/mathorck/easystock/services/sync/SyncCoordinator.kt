package ch.mathorck.easystock.services.sync

import kotlin.concurrent.Volatile
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock for everything that writes server data: the sync cycle, the photo downloads, and the wipe
 * of a logout, a revoked session or a reset. A wipe stops the running cycle first ([stopAndLock]), so
 * no pulled page is written after the data is erased.
 */
object SyncCoordinator {
    private val mutex = Mutex()

    @Volatile
    private var running: Job? = null

    /** Runs [block] alone, as the running sync job [job]. */
    internal suspend fun <T> cycle(job: Job?, block: suspend () -> T): T = mutex.withLock {
        running = job
        try {
            block()
        } finally {
            running = null
        }
    }

    /**
     * Runs [block] alone, after the running cycle, without cancelling it. For a sync state change outside
     * a cycle (a promotion resets `since`): a cycle running meanwhile would save its own `since` over it.
     */
    suspend fun <T> afterCycle(block: suspend () -> T): T = cycle(null, block)

    /** Cancels the running cycle, then runs [block] alone. For the wipes. */
    suspend fun <T> stopAndLock(block: suspend () -> T): T {
        running?.cancel()
        return mutex.withLock { block() }
    }
}
