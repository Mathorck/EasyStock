package ch.mathorck.easystock.data.model.profiles

import ch.mathorck.easystock.enums.profiles.ProfileLayout
import kotlinx.serialization.Serializable

/** Id of the profile every install already has: its data keeps the names used before profiles existed. */
const val LEGACY_PROFILE_ID = "legacy"

/**
 * One set of data on the phone: its own stock, standalone or joined to its own server.
 * [name] is null until the user names it (shown as "Default").
 */
@Serializable
data class Profile(
    val id: String,
    val name: String?,
    val layout: ProfileLayout,
) {
    companion object {
        val LEGACY_DEFAULT = Profile(id = LEGACY_PROFILE_ID, name = null, layout = ProfileLayout.LEGACY)
    }
}
