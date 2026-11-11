package ch.mathorck.easystock.auth

import androidx.compose.runtime.compositionLocalOf
import ch.mathorck.easystock.composables.pages.SettingsSubpageId

/**
 * The permissions of this phone: standalone (no `sync_state` row, everything allowed) or connected with [role].
 * [role] is null when connected with a role this app version doesn't know (Guest rights).
 */
data class AppPermissions(val connected: Boolean, val role: Role?) {
    fun can(action: Action): Boolean = Permissions.can(action, connected, role)

    companion object {
        /** No server: everything is allowed, the UI is the standalone one. */
        val Standalone = AppPermissions(connected = false, role = null)

        /** Connected to a server with [role]. */
        fun connectedAs(role: Role?) = AppPermissions(connected = true, role = role)

        /** From `sync_state.role`: null means no `sync_state` row, so standalone. */
        fun fromStoredRole(value: String?): AppPermissions =
            if (value == null) Standalone else connectedAs(Role.fromDb(value))
    }
}

/** Permissions of the current session, provided by MainComposable from `sync_state`. Standalone by default. */
val LocalPermissions = compositionLocalOf { AppPermissions.Standalone }

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
) {
    companion object {
        fun of(permissions: AppPermissions) = ArticleDetailGates(
            edit = permissions.can(Action.EDIT),
            stockActions = permissions.can(Action.STOCK_ACTION),
            linkTag = permissions.can(Action.LINK_TAG),
            history = permissions.can(Action.VIEW_HISTORY),
            infoFlags = permissions.can(Action.VIEW_REVIEWS),
            markInfoFixed = permissions.can(Action.EDIT),
        )
    }
}

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

/** Buttons of the stock lists (Stock page, location contents). */
data class StockListGates(
    /** "New article" button of the Stock page. */
    val createArticle: Boolean,
    /** Add, remove and move buttons on the rows of a location. */
    val stockActions: Boolean,
) {
    companion object {
        fun of(permissions: AppPermissions) = StockListGates(
            createArticle = permissions.can(Action.EDIT),
            stockActions = permissions.can(Action.STOCK_ACTION),
        )
    }
}

/**
 * What the settings list shows. Language and app info are always there; the catalogue screens,
 * the backup (export and import) and the reset of all data change data, so they need [Action.MANAGE_SETTINGS].
 */
data class SettingsGates(
    val subpages: Set<SettingsSubpageId>,
    /** "Reset all data" in app info. */
    val resetData: Boolean,
) {
    val catalogue: Boolean get() = CATALOGUE.any { it in subpages }
    val data: Boolean get() = SettingsSubpageId.Reviews in subpages || SettingsSubpageId.Backup in subpages

    /** The Administration entry of the "Server" section (the section itself, with its Server entry, always shows). */
    val server: Boolean get() = SettingsSubpageId.Administration in subpages

    companion object {
        private val CATALOGUE = setOf(
            SettingsSubpageId.Attributes,
            SettingsSubpageId.Categories,
            SettingsSubpageId.Locations,
            SettingsSubpageId.Tags,
        )

        fun of(permissions: AppPermissions): SettingsGates {
            val manage = permissions.can(Action.MANAGE_SETTINGS)
            val subpages = SettingsSubpageId.entries.filterTo(mutableSetOf()) { page ->
                when (page) {
                    SettingsSubpageId.Attributes, SettingsSubpageId.Categories,
                    SettingsSubpageId.Locations, SettingsSubpageId.Tags, SettingsSubpageId.Backup,
                    -> manage
                    SettingsSubpageId.Reviews -> permissions.can(Action.VIEW_REVIEWS)
                    // Connect (standalone) or the own account (connected): every role
                    SettingsSubpageId.AppInfo, SettingsSubpageId.Server -> true
                    // Server administration: only an Admin of a server. Never in standalone, where
                    // Action.ADMINISTER is allowed but there is no server to administer
                    SettingsSubpageId.Administration -> permissions.connected && permissions.role == Role.ADMIN
                }
            }
            return SettingsGates(subpages, resetData = manage)
        }
    }
}
