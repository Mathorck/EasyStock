package ch.mathorck.easystock.services.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds the sessions of the phone's profiles. Only the legacy "Default" profile exists for now. */
class ProfileManager(registry: ProfileRegistry = ProfileRegistry.legacyOnly()) {
    private val sessions = registry.profiles.associate { it.id to ProfileSession(it) }
    private val _active = MutableStateFlow(sessions.getValue(registry.activeId))

    val active: StateFlow<ProfileSession> get() = _active

    fun session(id: String): ProfileSession? = sessions[id]
}

/** The manager of the process. */
val profileManager = ProfileManager()

/** Names of the profile in use. */
val activeProfilePaths: ProfilePaths get() = profileManager.active.value.paths
