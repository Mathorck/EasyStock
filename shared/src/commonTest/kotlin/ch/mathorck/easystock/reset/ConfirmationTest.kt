package ch.mathorck.easystock.reset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfirmationTest {
    @Test
    fun exactWordMatches() {
        assertTrue(matchesConfirmation("RESET", "RESET"))
        assertTrue(matchesConfirmation("RÉINITIALISER", "RÉINITIALISER"))
        assertTrue(matchesConfirmation("ZURÜCKSETZEN", "ZURÜCKSETZEN"))
    }

    @Test
    fun caseAndSurroundingSpacesAreIgnored() {
        assertTrue(matchesConfirmation("reset", "RESET"))
        assertTrue(matchesConfirmation("  Reset \n", "RESET"))
        assertTrue(matchesConfirmation("réinitialiser", "RÉINITIALISER"))
    }

    @Test
    fun accentsAreOptional() {
        assertTrue(matchesConfirmation("REINITIALISER", "RÉINITIALISER"))
        assertTrue(matchesConfirmation("reinitialiser", "RÉINITIALISER"))
        assertTrue(matchesConfirmation("RÈINITIALISER", "RÉINITIALISER"))
        assertTrue(matchesConfirmation("zurucksetzen", "ZURÜCKSETZEN"))
    }

    @Test
    fun umlautCanBeTypedAsTwoLetters() {
        assertTrue(matchesConfirmation("ZURUECKSETZEN", "ZURÜCKSETZEN"))
        assertTrue(matchesConfirmation("zurueckSetzen", "ZURÜCKSETZEN"))
        // Only for an umlaut of the word: "E" is not a free extra letter
        assertFalse(matchesConfirmation("REESET", "RESET"))
    }

    @Test
    fun otherInputsDoNotMatch() {
        assertFalse(matchesConfirmation("", "RESET"))
        assertFalse(matchesConfirmation("   ", "RESET"))
        assertFalse(matchesConfirmation("RESE", "RESET"))
        assertFalse(matchesConfirmation("RESETS", "RESET"))
        assertFalse(matchesConfirmation("RE SET", "RESET"))
        assertFalse(matchesConfirmation("REINITIALISE", "RÉINITIALISER"))
        // The word of another language does not match
        assertFalse(matchesConfirmation("RESET", "RÉINITIALISER"))
    }

    @Test
    fun blankWordNeverMatches() {
        assertFalse(matchesConfirmation("", ""))
        assertFalse(matchesConfirmation(" ", " "))
    }

    @Test
    fun foldRemovesCaseAndAccents() {
        assertEquals("REINITIALISER", foldConfirmation(" Réinitialiser "))
        assertEquals("ZURUCKSETZEN", foldConfirmation("zurücksetzen"))
        assertEquals("ZURUECKSETZEN", foldConfirmation("zurücksetzen", umlautAsTwoLetters = true))
    }
}
