package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.Profile
import ch.mathorck.easystock.enums.profiles.ProfileLayout
import kotlinx.serialization.json.Json

/** The profiles of the phone and the active one. Without a stored registry the phone has the legacy profile only. */
class ProfileRegistry private constructor(val profiles: List<Profile>, val activeId: String) {
    init {
        require(profiles.isNotEmpty()) { "A registry holds at least one profile" }
        require(profiles.map { it.id }.toSet().size == profiles.size) { "Profile ids must be unique" }
        require(profiles.any { it.id == activeId }) { "The active profile is not in the registry" }
        require(profiles.count { it.layout == ProfileLayout.LEGACY } <= 1) { "One legacy profile at most" }
        require(profiles.all { it.layout == ProfileLayout.LEGACY || ProfilePaths.isValidId(it.id) }) { "Invalid profile id" }
    }

    val active: Profile get() = profiles.first { it.id == activeId }

    fun find(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** Adds a new [ProfileLayout.ISOLATED] profile, not active. */
    fun adding(id: String, name: String): ProfileRegistry =
        ProfileRegistry(profiles + Profile(id = id, name = name, layout = ProfileLayout.ISOLATED), activeId)

    fun renaming(id: String, name: String): ProfileRegistry {
        require(find(id) != null) { "Unknown profile" }
        return ProfileRegistry(profiles.map { if (it.id == id) it.copy(name = name) else it }, activeId)
    }

    /** Removes a profile that is not the active one. */
    fun removing(id: String): ProfileRegistry {
        require(id != activeId) { "The active profile can't be removed" }
        require(find(id) != null) { "Unknown profile" }
        return ProfileRegistry(profiles.filterNot { it.id == id }, activeId)
    }

    fun activating(id: String): ProfileRegistry {
        require(find(id) != null) { "Unknown profile" }
        return ProfileRegistry(profiles, id)
    }

    fun toJson(): String = JSON.encodeToString(ProfileRegistryData.serializer(), ProfileRegistryData(profiles, activeId))

    override fun equals(other: Any?): Boolean = other is ProfileRegistry && other.profiles == profiles && other.activeId == activeId

    override fun hashCode(): Int = 31 * profiles.hashCode() + activeId.hashCode()

    companion object {
        /** Longest profile name, in characters. */
        const val MAX_NAME_LENGTH = 40

        private val JSON = Json { ignoreUnknownKeys = true }

        fun legacyOnly() = ProfileRegistry(listOf(Profile.LEGACY_DEFAULT), Profile.LEGACY_DEFAULT.id)

        /** The stored registry, null when [json] is null, unreadable or not valid. */
        fun fromJson(json: String?): ProfileRegistry? = json?.let {
            runCatching {
                val data = JSON.decodeFromString(ProfileRegistryData.serializer(), it)
                ProfileRegistry(data.profiles, data.activeId)
            }.getOrNull()
        }

        /** The trimmed name, null when it is blank or longer than [MAX_NAME_LENGTH]. */
        fun cleanName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
    }
}
