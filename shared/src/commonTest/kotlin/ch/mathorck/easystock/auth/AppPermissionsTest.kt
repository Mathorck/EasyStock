package ch.mathorck.easystock.auth

import ch.mathorck.easystock.composables.pages.SettingsSubpageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the UI shows for each role, written out as expected values (not computed from [Permissions]). */
class AppPermissionsTest {
    private val admin = AppPermissions.connectedAs(Role.ADMIN)
    private val editor = AppPermissions.connectedAs(Role.EDITOR)
    private val viewer = AppPermissions.connectedAs(Role.VIEWER)
    private val guest = AppPermissions.connectedAs(Role.GUEST)
    private val unknown = AppPermissions.connectedAs(null)

    private val allSubpages = SettingsSubpageId.entries.toSet()

    @Test
    fun storedRoleGivesThePermissions() {
        assertEquals(AppPermissions(connected = false, role = null), AppPermissions.fromStoredRole(null))
        assertEquals(AppPermissions.Standalone, AppPermissions.fromStoredRole(null))
        assertEquals(AppPermissions(connected = true, role = Role.ADMIN), AppPermissions.fromStoredRole("admin"))
        assertEquals(AppPermissions(connected = true, role = Role.EDITOR), AppPermissions.fromStoredRole("editor"))
        assertEquals(AppPermissions(connected = true, role = Role.VIEWER), AppPermissions.fromStoredRole("viewer"))
        assertEquals(AppPermissions(connected = true, role = Role.GUEST), AppPermissions.fromStoredRole("guest"))
        // A role from a newer server: connected, Guest rights
        assertEquals(AppPermissions(connected = true, role = null), AppPermissions.fromStoredRole("auditor"))
    }

    @Test
    fun standaloneShowsEverything() {
        val p = AppPermissions.Standalone
        assertEquals(ArticleDetailGates(edit = true, stockActions = true, linkTag = true, history = true, infoFlags = true, markInfoFixed = true), ArticleDetailGates.of(p))
        assertEquals(UnknownTagGates(link = true, create = true), UnknownTagGates.of(p))
        assertEquals(ReviewGates(view = true, run = true), ReviewGates.of(p))
        assertEquals(StockListGates(createArticle = true, stockActions = true), StockListGates.of(p))
        assertEquals(SettingsGates(allSubpages, resetData = true), SettingsGates.of(p))
        Action.entries.forEach { assertTrue(p.can(it), "$it") }
    }

    @Test
    fun articleDetailPerRole() {
        val all = ArticleDetailGates(edit = true, stockActions = true, linkTag = true, history = true, infoFlags = true, markInfoFixed = true)
        assertEquals(all, ArticleDetailGates.of(admin))
        assertEquals(all, ArticleDetailGates.of(editor))
        assertEquals(
            ArticleDetailGates(edit = false, stockActions = false, linkTag = false, history = true, infoFlags = true, markInfoFixed = false),
            ArticleDetailGates.of(viewer),
        )
        val none = ArticleDetailGates(edit = false, stockActions = false, linkTag = false, history = false, infoFlags = false, markInfoFixed = false)
        assertEquals(none, ArticleDetailGates.of(guest))
        assertEquals(none, ArticleDetailGates.of(unknown))
    }

    @Test
    fun unknownTagPerRole() {
        assertEquals(UnknownTagGates(link = true, create = true), UnknownTagGates.of(admin))
        assertEquals(UnknownTagGates(link = true, create = true), UnknownTagGates.of(editor))
        assertEquals(UnknownTagGates(link = false, create = false), UnknownTagGates.of(viewer))
        assertEquals(UnknownTagGates(link = false, create = false), UnknownTagGates.of(guest))
        assertEquals(UnknownTagGates(link = false, create = false), UnknownTagGates.of(unknown))
        assertTrue(UnknownTagGates.of(editor).any)
        assertFalse(UnknownTagGates.of(viewer).any)
        assertTrue(UnknownTagGates(link = true, create = false).any)
    }

    @Test
    fun reviewsPerRole() {
        assertEquals(ReviewGates(view = true, run = true), ReviewGates.of(admin))
        assertEquals(ReviewGates(view = true, run = true), ReviewGates.of(editor))
        assertEquals(ReviewGates(view = true, run = false), ReviewGates.of(viewer))
        assertEquals(ReviewGates(view = false, run = false), ReviewGates.of(guest))
        assertEquals(ReviewGates(view = false, run = false), ReviewGates.of(unknown))
    }

    @Test
    fun stockListsPerRole() {
        assertEquals(StockListGates(createArticle = true, stockActions = true), StockListGates.of(admin))
        assertEquals(StockListGates(createArticle = true, stockActions = true), StockListGates.of(editor))
        assertEquals(StockListGates(createArticle = false, stockActions = false), StockListGates.of(viewer))
        assertEquals(StockListGates(createArticle = false, stockActions = false), StockListGates.of(guest))
        assertEquals(StockListGates(createArticle = false, stockActions = false), StockListGates.of(unknown))
    }

    @Test
    fun settingsPerRole() {
        assertEquals(SettingsGates(allSubpages, resetData = true), SettingsGates.of(admin))
        assertEquals(SettingsGates(allSubpages, resetData = true), SettingsGates.of(editor))
        assertEquals(
            SettingsGates(setOf(SettingsSubpageId.Reviews, SettingsSubpageId.AppInfo), resetData = false),
            SettingsGates.of(viewer),
        )
        assertEquals(SettingsGates(setOf(SettingsSubpageId.AppInfo), resetData = false), SettingsGates.of(guest))
        assertEquals(SettingsGates(setOf(SettingsSubpageId.AppInfo), resetData = false), SettingsGates.of(unknown))
    }

    @Test
    fun settingsSectionsPerRole() {
        listOf(AppPermissions.Standalone, admin, editor).forEach {
            assertTrue(SettingsGates.of(it).catalogue, "$it")
            assertTrue(SettingsGates.of(it).data, "$it")
        }
        assertFalse(SettingsGates.of(viewer).catalogue)
        assertTrue(SettingsGates.of(viewer).data)
        assertFalse(SettingsGates.of(guest).catalogue)
        assertFalse(SettingsGates.of(guest).data)
    }
}
