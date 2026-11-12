package ch.mathorck.easystock.alerts

import ch.mathorck.easystock.auth.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertPermissionRuleTest {
    @Test
    fun aPromotedGuestIsAskedOnce() {
        var mark = nextGuestMark(false, connected = true, role = Role.GUEST)
        assertEquals(GuestMark(wasGuest = true, ask = false), mark)
        // Seen again (same role): still a Guest, nothing to ask
        mark = nextGuestMark(mark.wasGuest, true, Role.GUEST)
        assertFalse(mark.ask)
        mark = nextGuestMark(mark.wasGuest, true, Role.EDITOR)
        assertTrue(mark.ask)
        // The next emission of the same role does not ask again
        mark = nextGuestMark(mark.wasGuest, true, Role.EDITOR)
        assertEquals(GuestMark(wasGuest = false, ask = false), mark)
    }

    @Test
    fun everyRoleThatReceivesAlertsIsAsked() {
        for (role in listOf(Role.ADMIN, Role.EDITOR, Role.VIEWER)) {
            assertTrue(nextGuestMark(true, true, role).ask, "$role")
        }
    }

    @Test
    fun aRoleThatWasNeverGuestIsNotAsked() {
        for (role in Role.entries) assertFalse(nextGuestMark(false, true, role).ask, "$role")
    }

    @Test
    fun anUnknownRoleChangesNothing() {
        assertEquals(GuestMark(true, ask = false), nextGuestMark(true, true, null))
        assertEquals(GuestMark(false, ask = false), nextGuestMark(false, true, null))
    }

    @Test
    fun leavingTheServerForgetsTheGuest() {
        assertEquals(GuestMark(false, ask = false), nextGuestMark(true, connected = false, role = null))
    }

    @Test
    fun theSwitchIsForEveryoneButGuests() {
        assertFalse(canChooseAlerts(Role.GUEST))
        assertFalse(canChooseAlerts(null))
        assertTrue(canChooseAlerts(Role.VIEWER))
        assertTrue(canChooseAlerts(Role.EDITOR))
        assertTrue(canChooseAlerts(Role.ADMIN))
    }

    @Test
    fun theServerDefaultDependsOnTheRole() {
        assertFalse(alertsOn(Role.VIEWER, null))
        assertTrue(alertsOn(Role.EDITOR, null))
        assertTrue(alertsOn(Role.ADMIN, null))
        assertFalse(alertsOn(Role.GUEST, null))
        assertTrue(alertsOn(Role.VIEWER, true))
        assertFalse(alertsOn(Role.EDITOR, false))
    }
}
