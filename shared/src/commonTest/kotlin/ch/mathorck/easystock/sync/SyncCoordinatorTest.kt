package ch.mathorck.easystock.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {
    @Test
    fun aPromotionWaitsForTheRunningCycleWithoutCancellingIt() = runTest {
        val pullDone = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val cycle = launch {
            SyncCoordinator.cycle(coroutineContext[kotlinx.coroutines.Job]) {
                order += "pull"
                pullDone.await()
                order += "save since"
            }
        }
        runCurrent()
        launch { SyncCoordinator.afterCycle { order += "since = 0" } }
        runCurrent()
        assertEquals(listOf("pull"), order)

        pullDone.complete(Unit)
        advanceUntilIdle()
        // The reset comes last: the cycle can't save its `since` over it
        assertEquals(listOf("pull", "save since", "since = 0"), order)
        assertEquals(false, cycle.isCancelled)
    }
}
