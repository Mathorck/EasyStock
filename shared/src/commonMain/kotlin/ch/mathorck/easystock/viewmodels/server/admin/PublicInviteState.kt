package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.data.dto.server.PublicInvite
import ch.mathorck.easystock.data.dto.server.QrGrant

data class PublicInviteState(
    val invite: Loaded<PublicInvite> = Loaded(),
    val busy: Boolean = false,
    val failure: ActionFailure? = null,
    /**
     * The token of the printed code, kept in memory only while this screen is open. The server may keep
     * only a hash: then the QR code and the PDF are only possible right after [regenerate][PublicInviteViewModel.regenerate].
     */
    val grant: QrGrant? = null,
    /** The new code was made: the old printed sheets stop working. */
    val regenerated: Boolean = false,
    /** Name printed on the sheet (`server_name` setting, else `/server-info`). Null until known. */
    val serverName: String? = null,
)
