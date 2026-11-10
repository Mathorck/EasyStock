package ch.mathorck.easystock.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

class ParseQuantityTest {
    @Test
    fun wholeNumbers() {
        assertEquals(3_000L, parseQuantity("3"))
        assertEquals(0L, parseQuantity("0"))
        assertEquals(1_200_000L, parseQuantity("1200"))
    }

    @Test
    fun dotAndCommaAreBothDecimalSeparators() {
        assertEquals(1_500L, parseQuantity("1.5"))
        assertEquals(1_500L, parseQuantity("1,5"))
        assertEquals(2_050L, parseQuantity("2,05"))
    }

    @Test
    fun upToThreeDecimals() {
        assertEquals(1L, parseQuantity("0.001"))
        assertEquals(1_125L, parseQuantity("1.125"))
        assertNull(parseQuantity("1.1255"))
        assertNull(parseQuantity("0.0001"))
    }

    @Test
    fun missingWholeOrFractionPart() {
        assertEquals(500L, parseQuantity(".5"))
        assertEquals(500L, parseQuantity(",5"))
        assertEquals(2_000L, parseQuantity("2."))
        assertEquals(2_000L, parseQuantity("2,"))
        assertNull(parseQuantity("."))
        assertNull(parseQuantity(","))
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertEquals(4_250L, parseQuantity("  4.25 "))
    }

    @Test
    fun leadingZerosAreAccepted() {
        assertEquals(7_000L, parseQuantity("007"))
        assertEquals(1_000L, parseQuantity("0000000000001"))
    }

    @Test
    fun invalidTextIsRejected() {
        listOf("", "   ", "abc", "1.2.3", "1,2.3", "1 000", "1'000", "-1", "+1", "1e3", "1.5kg", "١٢", "Infinity", "NaN")
            .forEach { assertNull(parseQuantity(it), "'$it' should be rejected") }
    }

    @Test
    fun schemaLimitIsEnforced() {
        assertEquals(MAX_QUANTITY_MILLI, parseQuantity("999999999.999"))
        assertNull(parseQuantity("1000000000"))
        assertNull(parseQuantity("99999999999999999999999"))
    }
}
