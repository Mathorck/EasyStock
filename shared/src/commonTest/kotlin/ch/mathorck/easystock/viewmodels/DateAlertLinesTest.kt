package ch.mathorck.easystock.viewmodels

import kotlin.test.Test
import kotlin.test.assertEquals

class DateAlertLinesTest {
    @Test
    fun newLinesComeAfterTheOnesStillShown() {
        assertEquals(listOf("a", "b", "c"), mergeDateAlertLines(listOf("a", "b"), listOf("c")))
    }

    @Test
    fun aRepeatedLineIsKeptOnce() {
        assertEquals(listOf("a", "b"), mergeDateAlertLines(listOf("a"), listOf("a", "b")))
    }

    @Test
    fun onlyTheNewestLinesAreKept() {
        assertEquals(listOf("c", "d"), mergeDateAlertLines(listOf("a", "b"), listOf("c", "d"), max = 2))
    }
}
