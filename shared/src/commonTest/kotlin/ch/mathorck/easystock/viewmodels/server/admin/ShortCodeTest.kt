package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.auth.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortCodeTest {
    @Test
    fun caseSpacesAndDashesAreIgnored() {
        assertEquals("K74QX", normalizeShortCode(" k7-4qx "))
        assertTrue(shortCodesMatch("K7-4QX", "k7 4qx"))
        assertTrue(shortCodesMatch("K7-4QX", "K74QX"))
        assertTrue(shortCodesMatch("K7-4QX", "k7–4qx".replace('–', '-')))
    }

    @Test
    fun otherCodesDoNotMatch() {
        assertFalse(shortCodesMatch("K7-4QX", "K7-4QY"))
        assertFalse(shortCodesMatch("K7-4QX", "K7-4Q"))
        assertFalse(shortCodesMatch("K7-4QX", ""))
        assertFalse(shortCodesMatch(null, "K74QX"))
        assertFalse(shortCodesMatch("--", "--"))
    }

    @Test
    fun promoteOnlyWhenTheCodesMatch() {
        assertFalse(PromoteForm("K7-4QX").canPromote)
        assertFalse(PromoteForm("K7-4QX", "K7-4QZ").canPromote)
        assertTrue(PromoteForm("K7-4QX", "k74qx").canPromote)
        assertFalse(PromoteForm("K7-4QX", "k74qx", Role.GUEST).canPromote)
        assertFalse(PromoteForm(null, "k74qx").canPromote)
    }
}
