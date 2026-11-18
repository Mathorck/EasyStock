package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.LEGACY_PROFILE_ID
import ch.mathorck.easystock.data.model.profiles.Profile
import ch.mathorck.easystock.enums.profiles.ProfileLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileRegistryTest {
    @Test
    fun legacyOnlyHoldsTheDefaultProfileAsActive() {
        val registry = ProfileRegistry.legacyOnly()
        assertEquals(listOf(Profile.LEGACY_DEFAULT), registry.profiles)
        assertEquals(LEGACY_PROFILE_ID, registry.activeId)
        assertEquals(Profile.LEGACY_DEFAULT, registry.active)
    }

    @Test
    fun defaultProfileUsesTheLegacyLayoutAndNoName() {
        assertEquals(ProfileLayout.LEGACY, Profile.LEGACY_DEFAULT.layout)
        assertNull(Profile.LEGACY_DEFAULT.name)
    }

    @Test
    fun findReturnsNullForAnUnknownId() {
        val registry = ProfileRegistry.legacyOnly()
        assertEquals(Profile.LEGACY_DEFAULT, registry.find(LEGACY_PROFILE_ID))
        assertNull(registry.find("other"))
    }
}
