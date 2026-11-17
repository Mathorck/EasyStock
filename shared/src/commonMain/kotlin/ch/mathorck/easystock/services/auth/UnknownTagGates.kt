package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.enums.auth.Action

/** Actions offered for a scanned tag linked to nothing. */
data class UnknownTagGates(
    /** Link the tag to an existing article or location. */
    val link: Boolean,
    /** Create an article or a location with the tag. */
    val create: Boolean,
) {
    val any: Boolean get() = link || create

    companion object {
        fun of(permissions: AppPermissions) = UnknownTagGates(
            link = permissions.can(Action.LINK_TAG),
            create = permissions.can(Action.EDIT) && permissions.can(Action.LINK_TAG),
        )
    }
}
