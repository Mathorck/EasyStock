package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.enums.auth.Action

/** What the article detail screen shows. Hidden buttons are for comfort: the server checks everything again. */
data class ArticleDetailGates(
    /** Edit button of the top bar, photo change, unit edit. */
    val edit: Boolean,
    /** Add, remove, move, lend, return... on the locations and units. */
    val stockActions: Boolean,
    /** Link, replace and remove the NFC tag. */
    val linkTag: Boolean,
    /** Movement history section (with the "info to fix" events). */
    val history: Boolean,
    /** Open "info to fix" cards (they come from finished reviews). */
    val infoFlags: Boolean,
    /** "Fixed" button on the "info to fix" cards: it changes the article info. */
    val markInfoFixed: Boolean,
    /** "Report a problem" button: raise a flag at any time, not only in a review. */
    val reportProblem: Boolean,
    /** Open manual flags (problem notes) in the flag card, and their history entries. Not for a Guest. */
    val problems: Boolean,
    /** "Mark as fixed" on a manual flag. Same right as editing the article, a Viewer cannot. */
    val markProblemFixed: Boolean,
) {
    companion object {
        fun of(permissions: AppPermissions) = ArticleDetailGates(
            edit = permissions.can(Action.EDIT),
            stockActions = permissions.can(Action.STOCK_ACTION),
            linkTag = permissions.can(Action.LINK_TAG),
            history = permissions.can(Action.VIEW_HISTORY),
            infoFlags = permissions.can(Action.VIEW_REVIEWS),
            markInfoFixed = permissions.can(Action.EDIT),
            reportProblem = permissions.can(Action.FLAG_ARTICLE),
            problems = permissions.can(Action.FLAG_ARTICLE),
            markProblemFixed = permissions.can(Action.EDIT),
        )
    }
}
