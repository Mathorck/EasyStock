package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.LEGACY_PROFILE_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ProfileServiceTest {
    private val store = FakeStore()
    private val platform = FakePlatform()
    private val manager = ProfileManager()
    private val service = ProfileService(manager, store, platform) { ProfilePathsTest.ID_A }

    @Test
    fun createSavesANewProfileAndKeepsTheActiveOne() {
        val profile = service.create("  Workshop ")!!
        assertEquals("Workshop", profile.name)
        assertEquals(LEGACY_PROFILE_ID, manager.active.value.id)
        assertEquals(manager.registry.value, ProfileRegistry.fromJson(store.json))
        assertNull(service.create(" "))
    }

    @Test
    fun switchSavesTheNewActiveIdThenRestarts() {
        service.create("Workshop")
        service.switchTo(ProfilePathsTest.ID_A)
        assertEquals(ProfilePathsTest.ID_A, ProfileRegistry.fromJson(store.json)!!.activeId)
        // In this process the legacy profile stays open until the restart
        assertEquals(LEGACY_PROFILE_ID, manager.active.value.id)
        assertEquals(listOf(ProfilePaths.LEGACY), platform.stopped)
        assertTrue(platform.restarted)
    }

    @Test
    fun switchToTheActiveProfileDoesNothing() {
        service.switchTo(LEGACY_PROFILE_ID)
        assertFalse(platform.restarted)
        assertNull(store.json)
    }

    @Test
    fun deleteLeavesTheRegistryThenWipes() = runTest {
        service.create("Workshop")
        service.delete(ProfilePathsTest.ID_A)
        assertNull(manager.registry.value.find(ProfilePathsTest.ID_A))
        assertEquals(1, platform.wiped.size)
        assertEquals("profiles/${ProfilePathsTest.ID_A}", platform.wiped.single().filesRoot)
        // Saved before the wipe started
        assertEquals(manager.registry.value, platform.registryAtWipe)
    }

    @Test
    fun theStoredRegistryIsInstalledAtStart() {
        service.create("Workshop")
        service.switchTo(ProfilePathsTest.ID_A)
        val next = ProfileManager()
        ProfileService.install(next, store)
        assertEquals(ProfilePathsTest.ID_A, next.active.value.id)
        assertEquals("profiles/${ProfilePathsTest.ID_A}", next.active.value.paths.filesRoot)
    }

    @Test
    fun aBrokenRegistryKeepsTheLegacyProfile() {
        store.json = "not json"
        val next = ProfileManager()
        ProfileService.install(next, store)
        assertEquals(ProfilePaths.LEGACY, next.active.value.paths)
    }

    private class FakeStore : ProfileRegistryStore {
        var json: String? = null
        override fun load() = json
        override fun save(json: String) {
            this.json = json
        }
    }

    private inner class FakePlatform : ProfilePlatform {
        val wiped = mutableListOf<ProfilePaths>()
        val stopped = mutableListOf<ProfilePaths>()
        var restarted = false
        var registryAtWipe: ProfileRegistry? = null

        override suspend fun wipe(paths: ProfilePaths) {
            registryAtWipe = ProfileRegistry.fromJson(store.json)
            wiped += paths
        }

        override fun stopBackgroundWork(paths: ProfilePaths) {
            stopped += paths
        }

        override fun restart() {
            restarted = true
        }
    }
}
