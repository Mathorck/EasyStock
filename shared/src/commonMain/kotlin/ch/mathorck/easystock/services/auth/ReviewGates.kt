package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.enums.auth.Action

/** What the reviews screens allow. A finished review is read-only for everyone. */
data class ReviewGates(
    /** The Reviews screen entry in the settings. */
    val view: Boolean,
    /** Start a review, count lines, add found items, finish or cancel an open review. */
    val run: Boolean,
) {
    companion object {
        fun of(permissions: AppPermissions) = ReviewGates(
            view = permissions.can(Action.VIEW_REVIEWS),
            run = permissions.can(Action.RUN_REVIEW),
        )
    }
}
