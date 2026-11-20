package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.LEGACY_PROFILE_ID
import ch.mathorck.easystock.data.model.profiles.Profile
import ch.mathorck.easystock.enums.profiles.ProfileLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileRegistryChangesTest {
    private val id = ProfilePathsTest.ID_A
    private val base = ProfileRegistry.legacyOnly().adding(id, "Workshop")

    @Test
    fun addingKeepsTheActiveProfile() {
        assertEquals(LEGACY_PROFILE_ID, base.activeId)
        assertEquals(Profile(id, "Workshop", ProfileLayout.ISOLATED), base.find(id))
    }

    @Test
    fun anIdIsAddedOnce() {
        assertFailsWith<IllegalArgumentException> { base.adding(id, "Again") }
    }

    @Test
    fun theActiveProfileCantBeRemoved() {
        assertFailsWith<IllegalArgumentException> { base.removing(LEGACY_PROFILE_ID) }
        assertEquals(listOf(Profile.LEGACY_DEFAULT), base.removing(id).profiles)
    }

    @Test
    fun renamingAndActivating() {
        assertEquals("Home", base.renaming(LEGACY_PROFILE_ID, "Home").active.name)
        assertEquals(id, base.activating(id).activeId)
        assertFailsWith<IllegalArgumentException> { base.activating("unknown") }
    }

    @Test
    fun jsonRoundTrip() {
        val registry = base.activating(id)
        assertEquals(registry, ProfileRegistry.fromJson(registry.toJson()))
    }

    @Test
    fun brokenJsonIsNull() {
        assertNull(ProfileRegistry.fromJson(null))
        assertNull(ProfileRegistry.fromJson("{"))
        // Valid JSON, but its active profile is missing
        assertNull(ProfileRegistry.fromJson(base.toJson().replace("\"activeId\":\"legacy\"", "\"activeId\":\"gone\"")))
        // A name that would leave the profiles folder
        assertNull(ProfileRegistry.fromJson(base.toJson().replace(id, "../evil")))
    }

    @Test
    fun namesAreTrimmedAndBounded() {
        assertEquals("Shop", ProfileRegistry.cleanName("  Shop "))
        assertNull(ProfileRegistry.cleanName("   "))
        assertNull(ProfileRegistry.cleanName("x".repeat(ProfileRegistry.MAX_NAME_LENGTH + 1)))
    }
}
