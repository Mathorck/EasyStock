package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.Profile
import kotlinx.serialization.Serializable

/** Stored form of a [ProfileRegistry]. */
@Serializable
data class ProfileRegistryData(val profiles: List<Profile>, val activeId: String)
