package ch.mathorck.easystock.services.profiles

import ch.mathorck.easystock.data.model.profiles.Profile
import ch.mathorck.easystock.enums.profiles.ProfileLayout

/** What is open for one profile: its names. The services of the process use the active one. */
class ProfileSession(val profile: Profile) {
    val id: String get() = profile.id

    val paths: ProfilePaths = when (profile.layout) {
        ProfileLayout.LEGACY -> ProfilePaths.LEGACY
        ProfileLayout.ISOLATED -> ProfilePaths.forProfile(profile.id)
    }
}
