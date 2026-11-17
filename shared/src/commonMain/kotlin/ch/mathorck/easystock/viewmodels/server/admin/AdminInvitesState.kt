package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.data.dto.server.Invite

data class AdminInvitesState(
    val invites: Loaded<List<Invite>> = Loaded(),
    val busy: Boolean = false,
    val failure: ActionFailure? = null,
    /** The invite just created, to show full screen. Its token is only known now. */
    val created: Invite? = null,
)
