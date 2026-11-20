package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.enums.auth.Action
import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.enums.settings.SettingsSubpageId

/**
 * What the settings list shows. Language, profiles and app info are always there; the catalogue screens,
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
                    // The profiles belong to the phone, not to a server role: always reachable, so a
                    // restricted account can still go back to another stock
                    SettingsSubpageId.Profiles -> true
                    // Server administration: only an Admin of a server. Never in standalone, where
                    // Action.ADMINISTER is allowed but there is no server to administer
                    SettingsSubpageId.Administration -> permissions.connected && permissions.role == Role.ADMIN
                }
            }
            return SettingsGates(subpages, resetData = manage)
        }
    }
}
