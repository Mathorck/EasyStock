package ch.mathorck.easystock.util

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CancellationTest {
    @Test
    fun valueAndFailureAreWrapped() {
        assertEquals(2, runCatchingCancellable { 1 + 1 }.getOrNull())
        assertTrue(runCatchingCancellable<Int> { error("boom") }.isFailure)
    }

    @Test
    fun cancellationIsRethrown() {
        assertFailsWith<CancellationException> { runCatchingCancellable { throw CancellationException("left") } }
    }
}
