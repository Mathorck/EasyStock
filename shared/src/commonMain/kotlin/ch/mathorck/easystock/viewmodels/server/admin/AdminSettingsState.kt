package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.data.dto.server.AdminSettings

data class AdminSettingsState(
    val settings: Loaded<AdminSettings> = Loaded(),
    val saving: Boolean = false,
    val failure: ActionFailure? = null,
    val saved: Boolean = false,
)
