package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.Profile
import ch.mathorck.easystock.enums.profiles.ProfileLayout

/** What is open for one profile. For now only its names: the services still are process-wide. */
class ProfileSession(val profile: Profile) {
    val id: String get() = profile.id

    val paths: ProfilePaths = when (profile.layout) {
        ProfileLayout.LEGACY -> ProfilePaths.LEGACY
    }
}
