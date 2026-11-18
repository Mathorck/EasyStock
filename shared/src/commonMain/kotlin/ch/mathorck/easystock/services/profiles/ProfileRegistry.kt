package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.Profile

/** The profiles of the phone. Nothing is stored yet: without a registry file the phone has the legacy profile only. */
class ProfileRegistry private constructor(val profiles: List<Profile>, val activeId: String) {
    init {
        require(profiles.isNotEmpty()) { "A registry holds at least one profile" }
        require(profiles.map { it.id }.toSet().size == profiles.size) { "Profile ids must be unique" }
        require(profiles.any { it.id == activeId }) { "The active profile is not in the registry" }
    }

    val active: Profile get() = profiles.first { it.id == activeId }

    fun find(id: String): Profile? = profiles.firstOrNull { it.id == id }

    companion object {
        fun legacyOnly() = ProfileRegistry(listOf(Profile.LEGACY_DEFAULT), Profile.LEGACY_DEFAULT.id)
    }
}
