package ch.mathorck.easystock.util

import kotlin.test.Test
import kotlin.test.assertEquals

class QuantityTest {
    @Test
    fun wholeNumbersHaveNoDecimals() {
        assertEquals("0", formatQuantity(0))
        assertEquals("3", formatQuantity(3_000))
        assertEquals("1200", formatQuantity(1_200_000))
    }

    @Test
    fun trailingZerosAreTrimmed() {
        assertEquals("1.5", formatQuantity(1_500))
        assertEquals("1.25", formatQuantity(1_250))
        assertEquals("1.125", formatQuantity(1_125))
    }

    @Test
    fun leadingZerosOfTheFractionAreKept() {
        assertEquals("0.005", formatQuantity(5))
        assertEquals("2.05", formatQuantity(2_050))
    }

    @Test
    fun negativeQuantitiesKeepTheirSign() {
        assertEquals("-0.5", formatQuantity(-500))
        assertEquals("-2", formatQuantity(-2_000))
    }
}
