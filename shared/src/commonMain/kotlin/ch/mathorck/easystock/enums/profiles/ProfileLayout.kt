package ch.mathorck.easystock.enums.profiles

import kotlinx.serialization.Serializable

/** Where a profile keeps its data. */
@Serializable
enum class ProfileLayout {
    /** The names of an install made before profiles (the "Default" profile). */
    LEGACY,

    /** Names of its own, made from the profile id (see `ProfilePaths.forProfile`). */
    ISOLATED,
}
