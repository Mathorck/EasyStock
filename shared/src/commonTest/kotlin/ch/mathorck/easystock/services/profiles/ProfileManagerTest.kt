package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.LEGACY_PROFILE_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProfileManagerTest {
    @Test
    fun activeSessionIsTheLegacyProfileWithLegacyPaths() {
        val session = ProfileManager().active.value
        assertEquals(LEGACY_PROFILE_ID, session.id)
        assertSame(ProfilePaths.LEGACY, session.paths)
    }

    @Test
    fun sessionByIdIsTheActiveOneAndUnknownIdIsNull() {
        val manager = ProfileManager()
        assertSame(manager.active.value, manager.session(LEGACY_PROFILE_ID))
        assertNull(manager.session("other"))
    }

    @Test
    fun processManagerRoutesToLegacyNames() {
        assertSame(ProfilePaths.LEGACY, activeProfilePaths)
    }
}
