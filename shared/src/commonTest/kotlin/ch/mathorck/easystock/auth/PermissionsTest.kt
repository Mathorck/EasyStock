package ch.mathorck.easystock.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionsTest {
    /** Expected rights of each role, written out action by action. */
    private val expected: Map<Role, Set<Action>> = mapOf(
        Role.ADMIN to Action.entries.toSet(),
        Role.EDITOR to setOf(
            Action.VIEW_STOCK,
            Action.VIEW_HISTORY,
            Action.VIEW_REVIEWS,
            Action.STOCK_ACTION,
            Action.EDIT,
            Action.DELETE,
            Action.LINK_TAG,
            Action.RUN_REVIEW,
            Action.MANAGE_SETTINGS,
            Action.PUSH_CHANGES,
            Action.RECEIVE_ALERTS,
        ),
        Role.VIEWER to setOf(Action.VIEW_STOCK, Action.VIEW_HISTORY, Action.VIEW_REVIEWS, Action.RECEIVE_ALERTS),
        Role.GUEST to setOf(Action.VIEW_STOCK),
    )

    @Test
    fun standaloneAllowsEverything() {
        Action.entries.forEach { action ->
            assertEquals(true, Permissions.can(action, connected = false, role = null), "$action")
            Role.entries.forEach { role ->
                assertEquals(true, Permissions.can(action, connected = false, role = role), "$role $action")
            }
        }
    }

    @Test
    fun everyRoleAndAction() {
        Role.entries.forEach { role ->
            Action.entries.forEach { action ->
                val allowed = action in expected.getValue(role)
                assertEquals(allowed, Permissions.can(action, connected = true, role = role), "$role $action")
            }
            assertEquals(expected.getValue(role), Permissions.allowed(role), "$role")
        }
    }

    @Test
    fun onlyTheAdminAdministers() {
        Role.entries.forEach { role ->
            assertEquals(role == Role.ADMIN, Permissions.can(Action.ADMINISTER, connected = true, role = role), "$role")
        }
    }

    @Test
    fun anUnknownRoleGetsTheGuestRights() {
        Action.entries.forEach { action ->
            assertEquals(action == Action.VIEW_STOCK, Permissions.can(action, connected = true, role = null), "$action")
        }
    }

    @Test
    fun rolesAreReadFromTheDatabaseValue() {
        assertEquals(Role.ADMIN, Role.fromDb("admin"))
        assertEquals(Role.EDITOR, Role.fromDb("editor"))
        assertEquals(Role.VIEWER, Role.fromDb("viewer"))
        assertEquals(Role.GUEST, Role.fromDb("guest"))
        assertNull(Role.fromDb("ADMIN"))
        assertNull(Role.fromDb("owner"))
        assertNull(Role.fromDb(""))
        assertNull(Role.fromDb(null))
    }
}
