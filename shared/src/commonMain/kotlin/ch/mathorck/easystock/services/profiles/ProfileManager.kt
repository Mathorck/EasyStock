package ch.mathorck.easystock.services.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The profiles of the phone. The active profile is set once, before any service opens its data
 * ([install], at the start of the process): switching profile restarts the process.
 */
class ProfileManager(registry: ProfileRegistry = ProfileRegistry.legacyOnly()) {
    private val _registry = MutableStateFlow(registry)
    private val _active = MutableStateFlow(ProfileSession(registry.active))

    val registry: StateFlow<ProfileRegistry> = _registry.asStateFlow()

    val active: StateFlow<ProfileSession> get() = _active

    fun session(id: String): ProfileSession? = when (id) {
        _active.value.id -> _active.value
        else -> _registry.value.find(id)?.let(::ProfileSession)
    }

    /** The stored registry, read at the start of the process. Its active profile becomes the one in use. */
    fun install(registry: ProfileRegistry) {
        _registry.value = registry
        if (registry.activeId != _active.value.id) _active.value = ProfileSession(registry.active)
    }

    /** A change that keeps the active profile (add, rename, remove another one). */
    fun update(registry: ProfileRegistry) {
        require(registry.activeId == _active.value.id) { "The active profile changes only at the start of the process" }
        _registry.value = registry
    }
}

/** The manager of the process. */
val profileManager = ProfileManager()

/** Names of the profile in use. */
val activeProfilePaths: ProfilePaths get() = profileManager.active.value.paths
