package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.Profile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow

/** Adds, renames, deletes and switches the profiles of the phone. */
@OptIn(ExperimentalUuidApi::class)
class ProfileService(
    private val manager: ProfileManager,
    private val store: ProfileRegistryStore,
    private val platform: ProfilePlatform,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    val registry: StateFlow<ProfileRegistry> get() = manager.registry

    /** Adds an empty standalone profile; the active one stays. Null when [name] is not valid. */
    fun create(name: String): Profile? {
        val clean = ProfileRegistry.cleanName(name) ?: return null
        val id = newId()
        val next = manager.registry.value.adding(id, clean)
        save(next)
        return next.find(id)
    }

    /** False when [name] is not valid. */
    fun rename(id: String, name: String): Boolean {
        val clean = ProfileRegistry.cleanName(name) ?: return false
        save(manager.registry.value.renaming(id, clean))
        return true
    }

    /**
     * Deletes a profile that is not the active one with all its data on the phone. It leaves the
     * registry first: a wipe that stops halfway never leaves a profile pointing at half its data.
     */
    suspend fun delete(id: String) {
        val current = manager.registry.value
        val profile = current.find(id) ?: return
        save(current.removing(id))
        platform.wipe(ProfileSession(profile).paths)
    }

    /** Makes [id] the active profile and restarts the app. Nothing happens for the active one. */
    fun switchTo(id: String) {
        val current = manager.registry.value
        if (id == current.activeId || current.find(id) == null) return
        store.save(current.activating(id).toJson())
        platform.stopBackgroundWork(manager.active.value.paths)
        platform.restart()
    }

    private fun save(registry: ProfileRegistry) {
        store.save(registry.toJson())
        manager.update(registry)
    }

    companion object {
        /** Reads the stored registry into [manager]. A missing or broken one keeps the legacy profile. */
        fun install(manager: ProfileManager, store: ProfileRegistryStore) {
            ProfileRegistry.fromJson(store.load())?.let(manager::install)
        }

    }
}
